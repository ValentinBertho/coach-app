package com.coachrun.integration;

import com.coachrun.dto.response.CalculatedBlockResponse;
import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.dto.response.CalculatedSessionResponse.CalculatedBlockEntry;
import com.coachrun.dto.response.CalculatedSessionResponse.CalculatedStepEntry;
import com.coachrun.dto.session.CourseBlock;
import com.coachrun.dto.session.CourseRecovery;
import com.coachrun.dto.session.CourseStep;
import com.coachrun.entity.WorkoutStep;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Séance course Darilab → « course » COROS, l'objet que prennent les outils MCP
 * {@code createScheduledWorkout} et {@code updateScheduledWorkout}.
 *
 * <p>Fonction pure, comme {@link GarminWorkoutMapper}, et à partir de la même entrée
 * ({@link GarminWorkoutMapper.Source}).</p>
 *
 * <h2>Ce que l'on sait du format</h2>
 *
 * <p>COROS ne publie pas le schéma de {@code course} : il ne se lit que dans {@code tools/list},
 * une fois connecté. Les codes ci-dessous viennent d'une intégration tierce qui fonctionne en
 * production ({@code sportType} 1 = course, {@code sectionType} 1/2/4 = échauffement / corps /
 * retour au calme, {@code targetType} 1 = distance en mètres, {@code intensityType} 2 = allure en
 * s/km). Les autres codes (durée, récupération, fréquence cardiaque) sont lus dans le schéma
 * live par {@link Vocabulary#fromSchema(JsonNode)} ; à défaut, on reste sur ce qui est sûr.</p>
 *
 * <h2>Choix de traduction</h2>
 * <ul>
 *   <li>Les répétitions sont <b>déroulées</b> en sections successives : la structure imbriquée
 *       de COROS ({@code sets}) n'est pas documentée, une liste à plat l'est.</li>
 *   <li>Cible chiffrée : l'allure calculée pour l'athlète. La fréquence cardiaque n'est envoyée
 *       comme cible que si le schéma en donne le code ; sinon elle figure dans la description.</li>
 *   <li>La description de la séance reprend le détail lisible bloc par bloc : même si la montre
 *       ne montre qu'une partie de la structure, l'athlète a toute la prescription.</li>
 * </ul>
 */
public final class CorosWorkoutMapper {

    /** Au-delà, la séance est envoyée sous sa forme simple (cf. {@link #toSimpleCourse}). */
    public static final int MAX_SECTIONS = 50;
    static final int NAME_MAX = 60;
    static final int DESCRIPTION_MAX = 1000;

    private CorosWorkoutMapper() {
    }

    /**
     * Codes de l'objet {@code course}. Les valeurs par défaut sont celles qu'on sait acceptées ;
     * celles marquées nulles ne sont utilisées que si le schéma live les donne.
     */
    public record Vocabulary(int sportRunning, int sectionWarmup, int sectionTraining, int sectionRecovery,
                             int sectionCooldown, int targetDistance, Integer targetTime,
                             int intensityPace, Integer intensityHeartRate) {

        public static final Vocabulary DEFAULT = new Vocabulary(1, 1, 2, 2, 4, 1, null, 2, null);

        /**
         * Complète les codes par ce que dit le schéma d'entrée de {@code createScheduledWorkout}
         * (descriptions du type « 1=distance (m), 2=time (s) » ou listes {@code enum}).
         */
        public static Vocabulary fromSchema(JsonNode inputSchema) {
            Map<Integer, String> sections = codes(inputSchema, "sectionType");
            Map<Integer, String> targets = codes(inputSchema, "targetType");
            Map<Integer, String> intensities = codes(inputSchema, "intensityType");
            Map<Integer, String> sports = codes(inputSchema, "sportType");
            Vocabulary d = DEFAULT;
            return new Vocabulary(
                    find(sports, d.sportRunning(), "run"),
                    find(sections, d.sectionWarmup(), "warm"),
                    find(sections, d.sectionTraining(), "train", "work", "main", "active"),
                    find(sections, d.sectionRecovery(), "recover", "rest"),
                    find(sections, d.sectionCooldown(), "cool"),
                    find(targets, d.targetDistance(), "distance"),
                    findOrNull(targets, d.targetTime(), "time", "duration"),
                    find(intensities, d.intensityPace(), "pace"),
                    findOrNull(intensities, d.intensityHeartRate(), "heart", "hr"));
        }

        private static int find(Map<Integer, String> codes, int fallback, String... words) {
            Integer v = findOrNull(codes, null, words);
            return v != null ? v : fallback;
        }

        private static Integer findOrNull(Map<Integer, String> codes, Integer fallback, String... words) {
            for (Map.Entry<Integer, String> e : codes.entrySet()) {
                String label = e.getValue().toLowerCase(Locale.ROOT);
                for (String w : words) {
                    if (label.startsWith(w) || label.contains(" " + w)) {
                        return e.getKey();
                    }
                }
            }
            return fallback;
        }

        private static final Pattern CODE = Pattern.compile("(\\d+)\\s*(?:=|:|-|→|->)\\s*([A-Za-z][A-Za-z ]{1,30})");

        /** Toutes les paires « code = libellé » trouvées pour ce champ, où qu'il soit dans le schéma. */
        static Map<Integer, String> codes(JsonNode schema, String field) {
            Map<Integer, String> out = new LinkedHashMap<>();
            collect(schema, field, out);
            return out;
        }

        private static void collect(JsonNode node, String field, Map<Integer, String> out) {
            if (node == null) {
                return;
            }
            if (node.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = node.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    if (e.getKey().equals(field) && e.getValue().isObject()) {
                        JsonNode desc = e.getValue().get("description");
                        if (desc != null && desc.isTextual()) {
                            Matcher m = CODE.matcher(desc.asText());
                            while (m.find()) {
                                out.putIfAbsent(Integer.valueOf(m.group(1)), m.group(2).trim());
                            }
                        }
                    }
                    collect(e.getValue(), field, out);
                }
            } else if (node.isArray()) {
                for (JsonNode child : node) {
                    collect(child, field, out);
                }
            }
        }
    }

    /**
     * Construit le {@code course} structuré.
     *
     * @throws IllegalArgumentException si la séance ne peut pas être décrite (ni distance ni durée
     *                                  exploitable) ou dépasse {@link #MAX_SECTIONS}
     */
    public static Map<String, Object> toCourse(GarminWorkoutMapper.Source source, Vocabulary v) {
        List<Map<String, Object>> sections = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        build(source, v, sections, lines);
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("Séance sans distance ni durée : rien à envoyer sur la montre.");
        }
        if (sections.size() > MAX_SECTIONS) {
            throw new IllegalArgumentException("Séance trop longue pour COROS : " + sections.size()
                    + " étapes (" + MAX_SECTIONS + " au plus).");
        }
        return course(source, v, sections, lines);
    }

    /**
     * Forme de repli : une seule section sur la distance (ou la durée) totale, sans cible, et toute
     * la prescription en description. Sert quand COROS refuse la forme structurée.
     */
    public static Map<String, Object> toSimpleCourse(GarminWorkoutMapper.Source source, Vocabulary v) {
        List<Map<String, Object>> ignored = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        build(source, v, ignored, lines);
        Integer distance = totalDistance(source, ignored, v);
        Map<String, Object> only;
        if (distance != null && distance > 0) {
            only = section(v.sectionTraining(), v.targetDistance(), distance, null, v);
        } else {
            Integer seconds = totalDuration(source, ignored, v);
            if (seconds == null || seconds <= 0 || v.targetTime() == null) {
                throw new IllegalArgumentException("Séance sans distance exploitable : rien à envoyer sur la montre.");
            }
            only = section(v.sectionTraining(), v.targetTime(), seconds, null, v);
        }
        return course(source, v, List.of(only), lines);
    }

    /** Ce qu'on écrit à la place d'une séance retirée : COROS ne permet pas de la supprimer. */
    public static Map<String, Object> cancelledCourse(String originalTitle, Vocabulary v) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("sportType", v.sportRunning());
        c.put("courseName", truncate("Annulée par ton coach", NAME_MAX));
        String what = blankToNull(originalTitle) != null ? "« " + originalTitle.trim() + " » a été" : "Cette séance a été";
        c.put("courseDescription", truncate(what + " retirée par ton coach dans Darilab. "
                + "COROS ne nous permet pas de la supprimer : tu peux l'effacer de ton calendrier dans l'app COROS.",
                DESCRIPTION_MAX));
        c.put("sections", List.of(section(v.sectionTraining(), v.targetDistance(), 1000, null, v)));
        return c;
    }

    // --- Construction -------------------------------------------------------------------------

    private static Map<String, Object> course(GarminWorkoutMapper.Source s, Vocabulary v,
                                              List<Map<String, Object>> sections, List<String> lines) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("sportType", v.sportRunning());
        c.put("courseName", truncate(blankToNull(s.title()) != null ? s.title().trim() : "Séance", NAME_MAX));
        StringBuilder desc = new StringBuilder();
        if (blankToNull(s.notes()) != null) {
            desc.append(s.notes().trim());
        }
        if (!lines.isEmpty()) {
            if (!desc.isEmpty()) {
                desc.append("\n\n");
            }
            desc.append(String.join("\n", lines));
        }
        desc.append(desc.isEmpty() ? "" : "\n").append("Envoyée par Darilab.");
        c.put("courseDescription", truncate(desc.toString(), DESCRIPTION_MAX));
        c.put("sections", sections);
        return c;
    }

    private static void build(GarminWorkoutMapper.Source s, Vocabulary v,
                              List<Map<String, Object>> out, List<String> lines) {
        CalculatedSessionResponse calc = s.calculated();
        if (calc != null && hasBlocks(calc)) {
            addEntries(out, lines, calc.warmup(), v.sectionWarmup(), "Échauffement", v);
            addEntries(out, lines, calc.main(), v.sectionTraining(), null, v);
            addEntries(out, lines, calc.cooldown(), v.sectionCooldown(), "Retour au calme", v);
            if (!out.isEmpty()) {
                return;
            }
        }
        if (s.steps() != null && !s.steps().isEmpty()) {
            for (WorkoutStep st : s.steps()) {
                fromLegacyStep(out, lines, st, v);
            }
            if (!out.isEmpty()) {
                return;
            }
        }
        Map<String, Object> only = plain(v.sectionTraining(), s.targetDistanceM(), s.targetDurationS(), null, v);
        if (only != null) {
            out.add(only);
        }
    }

    private static boolean hasBlocks(CalculatedSessionResponse c) {
        return !(isEmpty(c.warmup()) && isEmpty(c.main()) && isEmpty(c.cooldown()));
    }

    private static void addEntries(List<Map<String, Object>> out, List<String> lines,
                                   List<CalculatedBlockEntry> entries, int sectionType, String label, Vocabulary v) {
        if (entries == null) {
            return;
        }
        for (CalculatedBlockEntry e : entries) {
            if (e == null || e.block() == null) {
                continue;
            }
            addBlock(out, lines, e, sectionType, label, v);
        }
    }

    private static void addBlock(List<Map<String, Object>> out, List<String> lines, CalculatedBlockEntry e,
                                 int sectionType, String label, Vocabulary v) {
        CourseBlock b = e.block();
        int reps = Math.max(1, b.repCount());
        int sets = Math.max(1, b.setCount());
        List<Map<String, Object>> unit = new ArrayList<>();
        List<String> unitText = new ArrayList<>();
        if (b.isChain()) {
            for (CalculatedStepEntry se : e.steps()) {
                CourseStep st = se.step();
                if (st == null) {
                    continue;
                }
                addIfPresent(unit, plain(sectionType, st.distanceM(), st.durationS(), se.calc(), v));
                unitText.add(describe(st.distanceM(), st.durationS(), se.calc(), st.note()));
                if (st.recovery() != null) {
                    addIfPresent(unit, recovery(st.recovery(), se.recoveryCalc(), v));
                    unitText.add("récup " + describe(st.recovery().distanceM(), st.recovery().durationS(), null, null));
                }
            }
        } else {
            addIfPresent(unit, plain(sectionType, b.distanceM(), b.durationS(), e.calc(), v));
            unitText.add(describe(b.distanceM(), b.durationS(), e.calc(), b.note()));
            if (b.recovery() != null && (reps > 1 || sets > 1)) {
                addIfPresent(unit, recovery(b.recovery(), e.recoveryCalc(), v));
                unitText.add("récup " + describe(b.recovery().distanceM(), b.recovery().durationS(), null, null));
            }
        }
        for (int set = 1; set <= sets; set++) {
            for (int r = 1; r <= reps; r++) {
                for (Map<String, Object> sec : unit) {
                    out.add(new LinkedHashMap<>(sec));
                }
            }
            if (set < sets && b.setRecovery() != null) {
                addIfPresent(out, recovery(b.setRecovery(), null, v));
            }
        }
        String text = String.join(" + ", unitText);
        String prefix = (sets > 1 ? sets + " × " : "") + (reps > 1 ? reps + " × " : "");
        String between = sets > 1 && b.setRecovery() != null
                ? ", récup entre séries " + describe(b.setRecovery().distanceM(), b.setRecovery().durationS(), null, null)
                : "";
        lines.add("• " + (label != null ? label + " : " : "") + prefix + text + between);
    }

    private static void fromLegacyStep(List<Map<String, Object>> out, List<String> lines, WorkoutStep st,
                                       Vocabulary v) {
        int type = st.getStepType() == null ? v.sectionTraining() : switch (st.getStepType()) {
            case WARMUP -> v.sectionWarmup();
            case COOLDOWN -> v.sectionCooldown();
            case RECOVERY -> v.sectionRecovery();
            default -> v.sectionTraining();
        };
        Map<String, Object> sec = plain(type, st.getDistanceM(), st.getDurationS(), null, v);
        int reps = Math.max(1, st.getRepetitions());
        if (sec != null) {
            for (int i = 0; i < reps; i++) {
                out.add(new LinkedHashMap<>(sec));
            }
        }
        StringBuilder line = new StringBuilder("• ");
        if (reps > 1) {
            line.append(reps).append(" × ");
        }
        line.append(describe(st.getDistanceM(), st.getDurationS(), null, st.getNotes()));
        if (st.getZone() != null) {
            line.append(" (zone ").append(st.getZone().name()).append(')');
        }
        lines.add(line.toString());
    }

    private static Map<String, Object> recovery(CourseRecovery r, CalculatedBlockResponse calc, Vocabulary v) {
        return plain(v.sectionRecovery(), r.distanceM(), r.durationS(), calc, v);
    }

    /**
     * Une section : distance d'abord (c'est ainsi qu'un coach prescrit et c'est le code sûr),
     * puis durée si COROS en a donné le code, puis distance estimée. Null si rien d'exploitable.
     */
    private static Map<String, Object> plain(int sectionType, Integer distanceM, Integer durationS,
                                             CalculatedBlockResponse calc, Vocabulary v) {
        if (distanceM != null && distanceM > 0) {
            return section(sectionType, v.targetDistance(), distanceM, calc, v);
        }
        Integer seconds = durationS != null && durationS > 0 ? durationS
                : calc != null && calc.estimatedDurationS() != null && calc.estimatedDurationS() > 0
                        ? calc.estimatedDurationS() : null;
        if (seconds != null && v.targetTime() != null) {
            return section(sectionType, v.targetTime(), seconds, calc, v);
        }
        if (calc != null && calc.estimatedDistanceM() != null && calc.estimatedDistanceM() > 0) {
            return section(sectionType, v.targetDistance(), calc.estimatedDistanceM(), calc, v);
        }
        return null;
    }

    private static Map<String, Object> section(int sectionType, int targetType, int value,
                                               CalculatedBlockResponse calc, Vocabulary v) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("sectionType", sectionType);
        s.put("targetType", targetType);
        s.put("targetValue", value);
        if (calc != null && calc.computable()) {
            if (positive(calc.paceMinSecPerKm()) && positive(calc.paceMaxSecPerKm())) {
                s.put("intensityType", v.intensityPace());
                s.put("intensityValueStart", Math.min(calc.paceMinSecPerKm(), calc.paceMaxSecPerKm()));
                s.put("intensityValueEnd", Math.max(calc.paceMinSecPerKm(), calc.paceMaxSecPerKm()));
            } else if (v.intensityHeartRate() != null && positive(calc.hrMin()) && positive(calc.hrMax())) {
                s.put("intensityType", v.intensityHeartRate());
                s.put("intensityValueStart", Math.min(calc.hrMin(), calc.hrMax()));
                s.put("intensityValueEnd", Math.max(calc.hrMin(), calc.hrMax()));
            }
        }
        return s;
    }

    /** « 400 m à 3:45–3:55/km », « 20 min, FC 145–155 »… pour la description. */
    static String describe(Integer distanceM, Integer durationS, CalculatedBlockResponse calc, String note) {
        StringBuilder sb = new StringBuilder();
        if (distanceM != null && distanceM > 0) {
            sb.append(distanceM >= 1000 && distanceM % 100 == 0
                    ? String.format(Locale.FRANCE, "%s km", trimKm(distanceM)) : distanceM + " m");
        } else if (durationS != null && durationS > 0) {
            sb.append(durationS % 60 == 0 ? durationS / 60 + " min" : durationS / 60 + " min " + durationS % 60 + " s");
        }
        if (calc != null && calc.computable()) {
            if (positive(calc.paceMinSecPerKm()) && positive(calc.paceMaxSecPerKm())) {
                int fast = Math.min(calc.paceMinSecPerKm(), calc.paceMaxSecPerKm());
                int slow = Math.max(calc.paceMinSecPerKm(), calc.paceMaxSecPerKm());
                sb.append(sb.isEmpty() ? "" : " ").append("à ").append(pace(fast));
                if (slow != fast) {
                    sb.append('–').append(pace(slow));
                }
                sb.append("/km");
            }
            if (positive(calc.hrMin()) && positive(calc.hrMax())) {
                sb.append(sb.isEmpty() ? "" : ", ").append("FC ")
                        .append(Math.min(calc.hrMin(), calc.hrMax())).append('–')
                        .append(Math.max(calc.hrMin(), calc.hrMax()));
            }
        }
        if (blankToNull(note) != null) {
            sb.append(sb.isEmpty() ? "" : " — ").append(note.trim());
        }
        return sb.isEmpty() ? "libre" : sb.toString();
    }

    private static String trimKm(int meters) {
        return meters % 1000 == 0 ? String.valueOf(meters / 1000)
                : String.format(Locale.FRANCE, "%.1f", meters / 1000.0);
    }

    static String pace(int secPerKm) {
        return secPerKm / 60 + ":" + String.format("%02d", secPerKm % 60);
    }

    private static Integer totalDistance(GarminWorkoutMapper.Source s, List<Map<String, Object>> sections,
                                         Vocabulary v) {
        if (s.targetDistanceM() != null && s.targetDistanceM() > 0) {
            return s.targetDistanceM();
        }
        int sum = 0;
        for (Map<String, Object> sec : sections) {
            if (Integer.valueOf(v.targetDistance()).equals(sec.get("targetType"))) {
                sum += (Integer) sec.get("targetValue");
            } else {
                return null; // une partie en durée : la somme des distances ne dirait pas tout
            }
        }
        return sum > 0 ? sum : null;
    }

    private static Integer totalDuration(GarminWorkoutMapper.Source s, List<Map<String, Object>> sections,
                                         Vocabulary v) {
        if (s.targetDurationS() != null && s.targetDurationS() > 0) {
            return s.targetDurationS();
        }
        return null;
    }

    private static void addIfPresent(List<Map<String, Object>> out, Map<String, Object> section) {
        if (section != null) {
            out.add(section);
        }
    }

    private static boolean positive(Integer i) {
        return i != null && i > 0;
    }

    private static boolean isEmpty(List<?> l) {
        return l == null || l.isEmpty();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
