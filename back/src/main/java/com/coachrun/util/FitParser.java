package com.coachrun.util;

import com.coachrun.dto.response.ActivityLapsResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Décodeur <b>FIT</b> (Flexible and Interoperable Data Transfer) — le format natif des montres
 * Garmin et COROS, et le seul qui porte l'enregistrement complet : vitesse mesurée, altitude
 * barométrique, totaux calculés par la montre, tours réels.
 *
 * <p><b>Pourquoi un décodeur maison.</b> Le SDK Garmin n'est pas publié sur Maven Central et son
 * usage est soumis à l'acceptation d'une licence ; le reste du projet décode déjà GPX et TCX sans
 * dépendance. Le sous-ensemble nécessaire ici — en-tête, messages de définition, messages de
 * données, trois numéros de message globaux — tient en un fichier et n'a pas de raison de bouger :
 * le format est figé depuis 2010 et rétrocompatible par construction.</p>
 *
 * <p><b>Ce qu'il lit.</b> Les messages {@code record} (points), {@code lap} (tours) et
 * {@code session} (totaux). Tout le reste — profil de l'appareil, champs développeur, mesures de
 * cyclisme — est ignoré <i>en avançant du bon nombre d'octets</i>, ce qui est la seule chose que
 * le décodeur doive faire correctement d'un message qu'il ne comprend pas.</p>
 *
 * <p><b>Ce qu'il ne fait pas.</b> Aucune vérification de CRC : un fichier légèrement abîmé mais
 * lisible vaut mieux qu'un import refusé, et le CRC ne protège de rien qui nous concerne ici.</p>
 *
 * @see <a href="https://developer.garmin.com/fit/protocol/">FIT Protocol</a>
 */
public final class FitParser {

    /** Époque FIT : 31/12/1989 00:00:00 UTC, exprimée en secondes Unix. */
    private static final long FIT_EPOCH_S = 631_065_600L;

    /** Semi-cercles → degrés : 2^31 semi-cercles = 180°. */
    private static final double SEMICIRCLES_TO_DEG = 180.0 / 2_147_483_648.0;

    // Numéros de message globaux du profil FIT — les trois seuls qui nous intéressent.
    private static final int MSG_SESSION = 18;
    private static final int MSG_LAP = 19;
    private static final int MSG_RECORD = 20;

    private static final String INVALID = "Fichier FIT illisible.";

    private final byte[] c;
    private final Map<Integer, MessageDef> definitions = new HashMap<>();
    private final List<ActivityTrack.Point> points = new ArrayList<>();
    private final List<ActivityLapsResponse.Lap> laps = new ArrayList<>();

    /** Totaux de la montre : ils priment sur ce qu'on recalculerait depuis les points. */
    private Instant sessionStart;
    private Integer sessionDistanceM;
    private Integer sessionDurationS;
    private Integer sessionAscentM;
    private Integer sessionAvgHr;

    /** Distance cumulée du dernier point : repli quand le message de session manque. */
    private Double lastRecordDistanceM;

    /** Dernier horodatage vu, base des en-têtes à horodatage compressé. */
    private long lastTimestamp;

    private FitParser(byte[] content) {
        this.c = content;
    }

    /**
     * Signature d'un fichier FIT : les quatre octets {@code .FIT} à l'offset 8 de l'en-tête.
     * C'est le seul test fiable — l'extension ment (un {@code .fit} peut être un TCX renommé).
     */
    public static boolean looksLikeFit(byte[] content) {
        return content != null && content.length >= 14
                && content[8] == '.' && content[9] == 'F' && content[10] == 'I' && content[11] == 'T';
    }

    public static ActivityTrack.ParsedActivity parse(byte[] content) {
        if (!looksLikeFit(content)) {
            throw new IllegalArgumentException(INVALID);
        }
        return new FitParser(content).decode();
    }

    private ActivityTrack.ParsedActivity decode() {
        int headerSize = u8(0);
        if (headerSize < 12 || headerSize >= c.length) {
            throw new IllegalArgumentException(INVALID);
        }
        long dataSize = u32(4, false);
        int pos = headerSize;
        // Le CRC final (2 octets) n'est pas dans dataSize ; un fichier tronqué s'arrête plus tôt.
        int end = (int) Math.min(c.length, headerSize + dataSize);

        while (pos < end) {
            int next = readRecord(pos, end);
            if (next <= pos) {
                break; // définition manquante ou taille aberrante : on garde ce qu'on a lu
            }
            pos = next;
        }

        if (points.isEmpty() && sessionDurationS == null && sessionDistanceM == null) {
            throw new IllegalArgumentException("Fichier FIT sans séance exploitable.");
        }
        return assemble();
    }

    // --- Boucle de lecture -------------------------------------------------

    /** Lit un enregistrement (définition ou données) et renvoie la position suivante. */
    private int readRecord(int pos, int end) {
        int header = u8(pos++);
        if ((header & 0x80) != 0) {
            // En-tête à horodatage compressé : type local sur 2 bits, décalage de temps sur 5.
            MessageDef def = definitions.get((header >> 5) & 0x03);
            return def == null ? -1 : readData(pos, end, def, compressedTimestamp(header & 0x1F));
        }
        if ((header & 0x40) != 0) {
            return readDefinition(pos, end, header);
        }
        MessageDef def = definitions.get(header & 0x0F);
        return def == null ? -1 : readData(pos, end, def, null);
    }

    /**
     * Message de définition : il décrit la forme des messages de données qui suivront sous le
     * même « type local ». Sans lui, les octets de données ne veulent rien dire — c'est ce qui
     * rend le format compact, et impossible à lire par morceaux.
     */
    private int readDefinition(int pos, int end, int header) {
        if (pos + 5 > end) {
            return -1;
        }
        pos++; // octet réservé
        boolean bigEndian = u8(pos++) == 1;
        int globalNum = (int) u16(pos, bigEndian);
        pos += 2;
        int fieldCount = u8(pos++);
        if (pos + fieldCount * 3 > end) {
            return -1;
        }

        List<FieldDef> fields = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            fields.add(new FieldDef(u8(pos), u8(pos + 1), u8(pos + 2)));
            pos += 3;
        }

        // Champs développeur : inutiles ici, mais leurs octets occupent la place dans les
        // messages de données. Les ignorer sans compter leur taille décalerait toute la suite.
        int devBytes = 0;
        if ((header & 0x20) != 0) {
            if (pos >= end) {
                return -1;
            }
            int devCount = u8(pos++);
            if (pos + devCount * 3 > end) {
                return -1;
            }
            for (int i = 0; i < devCount; i++) {
                devBytes += u8(pos + 1);
                pos += 3;
            }
        }

        definitions.put(header & 0x0F, new MessageDef(globalNum, bigEndian, fields, devBytes));
        return pos;
    }

    /** Message de données : les champs, dans l'ordre exact de leur définition. */
    private int readData(int pos, int end, MessageDef def, Instant compressedTime) {
        int size = def.dataBytes();
        if (pos + size > end) {
            return -1;
        }
        switch (def.globalNum()) {
            case MSG_RECORD -> readRecordMessage(pos, def, compressedTime);
            case MSG_LAP -> readLapMessage(pos, def);
            case MSG_SESSION -> readSessionMessage(pos, def);
            default -> { /* profil, appareil, événements… : rien à en tirer ici */ }
        }
        return pos + size;
    }

    // --- Messages ----------------------------------------------------------

    private void readRecordMessage(int pos, MessageDef def, Instant compressedTime) {
        Instant time = compressedTime;
        Double lat = null;
        Double lon = null;
        Double altitude = null;
        Double enhancedAltitude = null;
        Double speed = null;
        Double enhancedSpeed = null;
        Integer hr = null;

        for (FieldDef f : def.fields()) {
            Double v = number(pos, f, def.bigEndian());
            pos += f.size();
            if (v == null) {
                continue;
            }
            switch (f.num()) {
                case 253 -> time = instant(v);
                case 0 -> lat = v * SEMICIRCLES_TO_DEG;
                case 1 -> lon = v * SEMICIRCLES_TO_DEG;
                case 2 -> altitude = v / 5.0 - 500;          // altitude, échelle 5, décalage 500
                case 78 -> enhancedAltitude = v / 5.0 - 500; // enhanced_altitude, même barème
                case 3 -> hr = v > 0 ? (int) Math.round(v) : null;
                case 5 -> lastRecordDistanceM = v / 100.0;   // distance cumulée, échelle 100
                case 6 -> speed = v / 1000.0;                // speed, échelle 1000 (m/s)
                case 73 -> enhancedSpeed = v / 1000.0;       // enhanced_speed, même barème
                default -> { /* cadence, puissance, température… : hors périmètre */ }
            }
        }

        if (time == null && lat == null) {
            return; // ni instant ni position : rien à en faire
        }
        if (time != null) {
            lastTimestamp = time.getEpochSecond();
        }
        // Les champs « enhanced » couvrent la plage complète (altitude négative, vitesse élevée) :
        // quand la montre les écrit, ce sont eux qui font foi.
        points.add(new ActivityTrack.Point(lat, lon,
                enhancedAltitude != null ? enhancedAltitude : altitude,
                time, hr,
                enhancedSpeed != null ? enhancedSpeed : speed));
    }

    private void readLapMessage(int pos, MessageDef def) {
        Double elapsed = null;
        Double timer = null;
        Double distance = null;
        Integer avgHr = null;
        Integer maxHr = null;
        Integer cadence = null;
        Integer ascent = null;

        for (FieldDef f : def.fields()) {
            Double v = number(pos, f, def.bigEndian());
            pos += f.size();
            if (v == null) {
                continue;
            }
            switch (f.num()) {
                case 7 -> elapsed = v / 1000.0;   // total_elapsed_time (montre en marche + pauses)
                case 8 -> timer = v / 1000.0;     // total_timer_time (temps en mouvement)
                case 9 -> distance = v / 100.0;
                case 15 -> avgHr = positive(v);
                case 16 -> maxHr = positive(v);
                case 17 -> cadence = positive(v);
                case 21 -> ascent = positive(v);
                default -> { /* autres totaux du tour : non exploités */ }
            }
        }

        Double duration = timer != null ? timer : elapsed;
        if (duration == null && distance == null) {
            return;
        }
        laps.add(ActivityLapsResponse.Lap.of(laps.size() + 1,
                distance != null ? (int) Math.round(distance) : null,
                duration != null ? (int) Math.round(duration) : null,
                avgHr, maxHr, cadence, ascent));
    }

    private void readSessionMessage(int pos, MessageDef def) {
        Double elapsed = null;
        Double timer = null;

        for (FieldDef f : def.fields()) {
            Double v = number(pos, f, def.bigEndian());
            pos += f.size();
            if (v == null) {
                continue;
            }
            switch (f.num()) {
                case 2 -> sessionStart = instant(v);
                case 7 -> elapsed = v / 1000.0;
                case 8 -> timer = v / 1000.0;
                case 9 -> sessionDistanceM = (int) Math.round(v / 100.0);
                case 16 -> sessionAvgHr = positive(v);
                case 22 -> sessionAscentM = positive(v);
                default -> { /* sport, calories, vitesse moyenne… : dérivables ou inutiles */ }
            }
        }
        Double duration = timer != null ? timer : elapsed;
        if (duration != null) {
            sessionDurationS = (int) Math.round(duration);
        }
    }

    // --- Assemblage --------------------------------------------------------

    /**
     * Les totaux de la montre priment sur les nôtres. Elle a l'odomètre, l'altimètre barométrique
     * et la pause automatique ; nous n'avons que des points GPS échantillonnés. Recalculer par
     * haversine une distance que la montre a déjà mesurée, c'est afficher 9,87 km là où l'athlète
     * a vu 10,00 — et rendre l'application moins crédible que le poignet qu'elle lit.
     */
    private ActivityTrack.ParsedActivity assemble() {
        List<ActivityTrack.Point> positioned = ActivityTrack.positioned(points);

        Instant first = points.isEmpty() ? null : points.get(0).time();
        Instant start = sessionStart != null ? sessionStart : first;
        LocalDate date = start != null ? start.atZone(ZoneOffset.UTC).toLocalDate() : LocalDate.now();

        Integer distanceM = sessionDistanceM;
        if (distanceM == null && lastRecordDistanceM != null) {
            distanceM = (int) Math.round(lastRecordDistanceM);
        }
        if (distanceM == null && positioned.size() > 1) {
            distanceM = ActivityTrack.distanceM(positioned);
        }

        Integer durationS = sessionDurationS;
        if (durationS == null && first != null) {
            Instant last = points.get(points.size() - 1).time();
            if (last != null) {
                durationS = (int) (last.getEpochSecond() - first.getEpochSecond());
            }
        }

        Integer ascent = sessionAscentM != null ? sessionAscentM : ActivityTrack.elevationGainM(points);
        Integer avgHr = sessionAvgHr != null ? sessionAvgHr : ActivityTrack.avgHr(points);

        return new ActivityTrack.ParsedActivity(date, distanceM, durationS, ascent, avgHr,
                ActivityTrack.downsample(positioned), ActivityTrack.buildStream(points),
                // Un tour unique n'en est pas un : la montre a enregistré la sortie d'un bloc.
                // Même règle que le TCX, pour que les deux formats se lisent pareil.
                laps.size() > 1 ? List.copyOf(laps) : List.of());
    }

    // --- Lecture bas niveau ------------------------------------------------

    /**
     * Valeur numérique d'un champ, ou {@code null} quand le fichier la marque « invalide ».
     *
     * <p>Chaque type de base FIT réserve une valeur pour dire « non mesuré » (0xFF sur un octet,
     * 0xFFFF sur deux…). La confondre avec une donnée donnerait une FC de 255 et une altitude de
     * 12 000 m — d'où un {@code null} explicite plutôt qu'un zéro silencieux.</p>
     *
     * <p>Un champ plus large que son type est un tableau : on n'en lit que le premier élément,
     * qui est celui du barème (les tableaux du profil FIT sont des séries auxiliaires).</p>
     */
    private Double number(int pos, FieldDef f, boolean bigEndian) {
        if (pos + f.size() > c.length) {
            return null;
        }
        switch (f.baseType() & 0x1F) {
            case 0, 2, 13: {                       // enum, uint8, byte
                long v = u8(pos);
                return v == 0xFF ? null : (double) v;
            }
            case 10: {                             // uint8z (0 = invalide)
                long v = u8(pos);
                return v == 0 ? null : (double) v;
            }
            case 1: {                              // sint8
                long v = c[pos];
                return v == 0x7F ? null : (double) v;
            }
            case 4: {                              // uint16
                long v = u16(pos, bigEndian);
                return v == 0xFFFFL ? null : (double) v;
            }
            case 11: {                             // uint16z
                long v = u16(pos, bigEndian);
                return v == 0 ? null : (double) v;
            }
            case 3: {                              // sint16
                long v = (short) u16(pos, bigEndian);
                return v == 0x7FFF ? null : (double) v;
            }
            case 6: {                              // uint32
                long v = u32(pos, bigEndian);
                return v == 0xFFFFFFFFL ? null : (double) v;
            }
            case 12: {                             // uint32z
                long v = u32(pos, bigEndian);
                return v == 0 ? null : (double) v;
            }
            case 5: {                              // sint32
                long v = (int) u32(pos, bigEndian);
                return v == 0x7FFFFFFFL ? null : (double) v;
            }
            case 8: {                              // float32
                float v = Float.intBitsToFloat((int) u32(pos, bigEndian));
                return Float.isNaN(v) ? null : (double) v;
            }
            default:
                return null;                       // chaînes, 64 bits : aucun champ utile ici
        }
    }

    /**
     * Horodatage d'un en-tête compressé : cinq bits de décalage sur la base du dernier instant
     * connu, avec report quand le compteur reboucle (toutes les 32 s).
     */
    private Instant compressedTimestamp(int offset) {
        if (lastTimestamp == 0) {
            return null;
        }
        long base = lastTimestamp & ~0x1FL;
        long previousOffset = lastTimestamp & 0x1FL;
        long ts = base + offset + (offset < previousOffset ? 0x20 : 0);
        lastTimestamp = ts;
        return Instant.ofEpochSecond(ts);
    }

    private static Instant instant(double fitSeconds) {
        return Instant.ofEpochSecond(FIT_EPOCH_S + (long) fitSeconds);
    }

    private static Integer positive(double v) {
        return v > 0 ? (int) Math.round(v) : null;
    }

    private int u8(int pos) {
        return c[pos] & 0xFF;
    }

    private long u16(int pos, boolean bigEndian) {
        return bigEndian
                ? ((long) u8(pos) << 8) | u8(pos + 1)
                : ((long) u8(pos + 1) << 8) | u8(pos);
    }

    private long u32(int pos, boolean bigEndian) {
        return bigEndian
                ? ((long) u8(pos) << 24) | ((long) u8(pos + 1) << 16) | ((long) u8(pos + 2) << 8) | u8(pos + 3)
                : ((long) u8(pos + 3) << 24) | ((long) u8(pos + 2) << 16) | ((long) u8(pos + 1) << 8) | u8(pos);
    }

    // --- Structures du format ----------------------------------------------

    /** Un champ tel que le message de définition le décrit : numéro, taille en octets, type. */
    private record FieldDef(int num, int size, int baseType) {
    }

    /** La forme d'un message de données, valable jusqu'à redéfinition du même type local. */
    private record MessageDef(int globalNum, boolean bigEndian, List<FieldDef> fields, int devBytes) {

        /** Taille totale d'un message de données, champs développeur compris. */
        int dataBytes() {
            int total = devBytes;
            for (FieldDef f : fields) {
                total += f.size();
            }
            return total;
        }
    }
}
