package com.coachrun;

import com.coachrun.FitFileBuilder.Field;
import com.coachrun.util.ActivityFileParser;
import com.coachrun.util.ActivityTrack;
import com.coachrun.util.FitParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.coachrun.FitFileBuilder.INVALID_U8;
import static com.coachrun.FitFileBuilder.TYPE_S32;
import static com.coachrun.FitFileBuilder.TYPE_U16;
import static com.coachrun.FitFileBuilder.TYPE_U32;
import static com.coachrun.FitFileBuilder.TYPE_U8;
import static com.coachrun.FitFileBuilder.fitTime;
import static com.coachrun.FitFileBuilder.semicircles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Décodage FIT — le format natif des montres Garmin et COROS.
 *
 * <p>Chaque test vise un piège précis du format binaire : les totaux de la montre qui doivent
 * primer sur nos calculs, les valeurs « non mesuré » qui ne sont pas des zéros, les messages
 * inconnus qu'il faut sauter au bon nombre d'octets, et la séance de salle sans le moindre point
 * GPS — que le chemin GPX/TCX refusait par construction.</p>
 */
class FitParserTest {

    private static final List<Field> RECORD = List.of(
            new Field(253, 4, TYPE_U32),   // timestamp
            new Field(0, 4, TYPE_S32),     // position_lat
            new Field(1, 4, TYPE_S32),     // position_long
            new Field(2, 2, TYPE_U16),     // altitude
            new Field(3, 1, TYPE_U8),      // heart_rate
            new Field(5, 4, TYPE_U32),     // distance
            new Field(6, 2, TYPE_U16));    // speed

    private static final List<Field> LAP = List.of(
            new Field(8, 4, TYPE_U32),     // total_timer_time
            new Field(9, 4, TYPE_U32),     // total_distance
            new Field(15, 1, TYPE_U8),     // avg_heart_rate
            new Field(16, 1, TYPE_U8),     // max_heart_rate
            new Field(17, 1, TYPE_U8));    // avg_cadence

    private static final List<Field> SESSION = List.of(
            new Field(2, 4, TYPE_U32),     // start_time
            new Field(8, 4, TYPE_U32),     // total_timer_time
            new Field(9, 4, TYPE_U32),     // total_distance
            new Field(16, 1, TYPE_U8),     // avg_heart_rate
            new Field(22, 2, TYPE_U16));   // total_ascent

    private static final long T0 = fitTime("2026-06-20T08:00:00Z");

    /** Altitude encodée : (mètres + 500) × 5. */
    private static long altitude(int meters) {
        return (meters + 500) * 5L;
    }

    @Test
    void litUneSortieDeMontreAvecSesToursEtSesTotaux() {
        byte[] fit = new FitFileBuilder()
                .define(0, 20, RECORD)
                .data(0, RECORD, T0, semicircles(45.7500), semicircles(4.8500), altitude(170), 142, 0, 3330)
                .data(0, RECORD, T0 + 150, semicircles(45.7536), semicircles(4.8520), altitude(180), 158, 50_000, 3330)
                .data(0, RECORD, T0 + 300, semicircles(45.7554), semicircles(4.8540), altitude(175), 165, 100_000, 3330)
                .define(1, 19, LAP)
                .data(1, LAP, 84_000, 40_000, 168, 176, 92)
                .data(1, LAP, 120_000, 20_000, 132, 150, 80)
                .define(2, 18, SESSION)
                .data(2, SESSION, T0, 3_600_000, 1_000_000, 150, 120)
                .build();

        ActivityTrack.ParsedActivity a = FitParser.parse(fit);

        assertThat(a.date().toString()).isEqualTo("2026-06-20");
        // Les totaux viennent de la montre, pas d'un haversine sur trois points : elle a
        // l'odomètre et l'altimètre barométrique, nous n'avons qu'un échantillonnage GPS.
        assertThat(a.distanceM()).isEqualTo(10_000);
        assertThat(a.durationS()).isEqualTo(3_600);
        assertThat(a.elevationGainM()).isEqualTo(120);
        assertThat(a.avgHr()).isEqualTo(150);

        assertThat(a.route()).hasSize(3);
        assertThat(a.route().get(0)[0]).isEqualTo(45.75);
        assertThat(a.route().get(0)[1]).isEqualTo(4.85);

        // [elapsedS, hr, paceSecPerKm] — l'allure vient de la vitesse mesurée (3,33 m/s → 5'00/km).
        assertThat(a.stream().get(0)).containsExactly(0, 142, 300);
        assertThat(a.stream().get(2)[0]).isEqualTo(300);

        assertThat(a.laps()).hasSize(2);
        assertThat(a.laps().get(0).distanceM()).isEqualTo(400);
        assertThat(a.laps().get(0).durationS()).isEqualTo(84);
        assertThat(a.laps().get(0).paceSPerKm()).isEqualTo(210);
        assertThat(a.laps().get(0).avgHr()).isEqualTo(168);
        assertThat(a.laps().get(0).maxHr()).isEqualTo(176);
        assertThat(a.laps().get(1).distanceM()).isEqualTo(200);
    }

    @Test
    void litUneSeanceDeSalleSansAucunPointGps() {
        // Tapis de course : la montre enregistre temps, distance et FC, jamais de position.
        // Le chemin GPX/TCX refusait ce fichier (« sans points GPS exploitables ») ; c'est
        // précisément ce que l'import FIT débloque.
        List<Field> indoor = List.of(
                new Field(253, 4, TYPE_U32),
                new Field(3, 1, TYPE_U8),
                new Field(5, 4, TYPE_U32));

        byte[] fit = new FitFileBuilder()
                .define(0, 20, indoor)
                .data(0, indoor, T0, 138, 0)
                .data(0, indoor, T0 + 600, 152, 200_000)
                .define(1, 18, SESSION)
                .data(1, SESSION, T0, 1_800_000, 400_000, 145, 0)
                .build();

        ActivityTrack.ParsedActivity a = FitParser.parse(fit);

        assertThat(a.route()).isEmpty();
        assertThat(a.distanceM()).isEqualTo(4_000);
        assertThat(a.durationS()).isEqualTo(1_800);
        assertThat(a.avgHr()).isEqualTo(145);
        // Le flux existe malgré l'absence de position : la FC alimente le temps-en-zone.
        assertThat(a.stream()).isNotEmpty();
        assertThat(a.stream().get(0)[1]).isEqualTo(138);
        assertThat(a.stream().get(0)[2]).isEqualTo(-1); // aucune allure dérivable
    }

    @Test
    void traiteLesValeursNonMesureesCommeAbsentesEtNonCommeDesZeros() {
        byte[] fit = new FitFileBuilder()
                .define(0, 20, RECORD)
                .data(0, RECORD, T0, semicircles(45.75), semicircles(4.85),
                        altitude(170), INVALID_U8, 0, 3330)
                .data(0, RECORD, T0 + 60, semicircles(45.76), semicircles(4.85),
                        altitude(175), INVALID_U8, 20_000, 3330)
                .build();

        ActivityTrack.ParsedActivity a = FitParser.parse(fit);

        // 0xFF sur un octet, c'est « pas de capteur » — pas une FC de 255.
        assertThat(a.avgHr()).isNull();
        assertThat(a.stream().get(0)[1]).isEqualTo(-1);
        // Sans message de session, la distance retombe sur le cumul du dernier point.
        assertThat(a.distanceM()).isEqualTo(200);
        assertThat(a.elevationGainM()).isEqualTo(5);
    }

    @Test
    void sauteLesMessagesInconnusSansPerdreLeFilDesSuivants() {
        // Un fichier de montre est plein de messages qui ne nous concernent pas (événements,
        // profil de l'appareil, capteurs). Mal les sauter décale tout ce qui suit.
        List<Field> event = List.of(new Field(0, 1, TYPE_U8), new Field(253, 4, TYPE_U32));

        byte[] fit = new FitFileBuilder()
                .define(0, 20, RECORD)
                .data(0, RECORD, T0, semicircles(45.75), semicircles(4.85), altitude(170), 140, 0, 3330)
                .define(3, 21, event)                       // message « event », inconnu du décodeur
                .data(3, event, 0, T0 + 10)
                .data(0, RECORD, T0 + 60, semicircles(45.76), semicircles(4.85), altitude(172), 150, 20_000, 3330)
                .build();

        ActivityTrack.ParsedActivity a = FitParser.parse(fit);

        assertThat(a.route()).hasSize(2);
        assertThat(a.stream()).hasSize(2);
        assertThat(a.stream().get(1)[1]).isEqualTo(150);
    }

    @Test
    void compteLesOctetsDesChampsDeveloppeurQuIlNExploitePas() {
        // Les champs développeur (Connect IQ) sont ignorés, mais leurs octets occupent la place
        // dans chaque message de données : ne pas les compter décalerait la lecture.
        byte[] fit = new FitFileBuilder()
                .define(0, 20, RECORD, List.of(4))
                .data(0, RECORD, T0, semicircles(45.75), semicircles(4.85), altitude(170), 140, 0, 3330)
                .raw(4)
                .data(0, RECORD, T0 + 60, semicircles(45.76), semicircles(4.85), altitude(172), 150, 20_000, 3330)
                .raw(4)
                .build();

        ActivityTrack.ParsedActivity a = FitParser.parse(fit);

        assertThat(a.route()).hasSize(2);
        assertThat(a.stream().get(1)[1]).isEqualTo(150);
        assertThat(a.distanceM()).isEqualTo(200);
    }

    @Test
    void litUnFichierGrosBoutiste() {
        // L'architecture est déclarée message par message : un fichier gros-boutiste est rare
        // mais légal, et le lire à l'envers donnerait des distances absurdes plutôt qu'une erreur.
        byte[] fit = new FitFileBuilder(true)
                .define(0, 20, RECORD)
                .data(0, RECORD, T0, semicircles(45.75), semicircles(4.85), altitude(170), 142, 0, 3330)
                .define(1, 18, SESSION)
                .data(1, SESSION, T0, 1_200_000, 300_000, 148, 42)
                .build();

        ActivityTrack.ParsedActivity a = FitParser.parse(fit);

        assertThat(a.date().toString()).isEqualTo("2026-06-20");
        assertThat(a.distanceM()).isEqualTo(3_000);
        assertThat(a.durationS()).isEqualTo(1_200);
        assertThat(a.avgHr()).isEqualTo(148);
        assertThat(a.elevationGainM()).isEqualTo(42);
    }

    @Test
    void refuseUnFichierQuiNaRienDUneSeance() {
        byte[] fit = new FitFileBuilder()
                .define(0, 21, List.of(new Field(0, 1, TYPE_U8)))
                .data(0, List.of(new Field(0, 1, TYPE_U8)), 1)
                .build();

        assertThatThrownBy(() -> FitParser.parse(fit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sans séance exploitable");
    }

    // --- Aiguillage par le contenu, jamais par l'extension -------------------

    @Test
    void aiguilleSurLaSignatureDuFichierEtNonSurSonNom() {
        byte[] fit = new FitFileBuilder()
                .define(0, 20, RECORD)
                .data(0, RECORD, T0, semicircles(45.75), semicircles(4.85), altitude(170), 142, 0, 3330)
                .define(1, 18, SESSION)
                .data(1, SESSION, T0, 600_000, 150_000, 140, 10)
                .build();
        assertThat(ActivityFileParser.parse(fit).distanceM()).isEqualTo(1_500);

        // Un TCX renommé « .fit » (cas courant des convertisseurs) reste lisible : c'est le
        // contenu qui décide.
        String tcx = """
                <?xml version="1.0"?>
                <TrainingCenterDatabase><Activities><Activity><Lap><Track>
                  <Trackpoint><Time>2026-06-20T08:00:00Z</Time>
                    <Position><LatitudeDegrees>45.75</LatitudeDegrees><LongitudeDegrees>4.85</LongitudeDegrees></Position>
                  </Trackpoint>
                  <Trackpoint><Time>2026-06-20T08:05:00Z</Time>
                    <Position><LatitudeDegrees>45.76</LatitudeDegrees><LongitudeDegrees>4.85</LongitudeDegrees></Position>
                  </Trackpoint>
                </Track></Lap></Activity></Activities></TrainingCenterDatabase>
                """;
        assertThat(ActivityFileParser.parse(tcx.getBytes(StandardCharsets.UTF_8)).durationS())
                .isEqualTo(300);
    }

    @Test
    void nommeLesFormatsAcceptesQuandLeFichierNEnEstAucun() {
        assertThatThrownBy(() -> ActivityFileParser.parse("%PDF-1.7 …".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".fit, .gpx, .tcx");

        assertThatThrownBy(() -> ActivityFileParser.parse(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vide");
    }
}
