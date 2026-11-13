package com.coachrun.integration;

import com.coachrun.dto.response.CalculatedBlockResponse;
import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.dto.response.CalculatedSessionResponse.CalculatedBlockEntry;
import com.coachrun.dto.response.CalculatedSessionResponse.CalculatedStepEntry;
import com.coachrun.dto.session.CourseBlock;
import com.coachrun.dto.session.CourseRecovery;
import com.coachrun.dto.session.CourseStep;
import com.coachrun.entity.WorkoutStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Séance course Darilab → entraînement Garmin (Training API V2, sport {@code RUNNING}).
 *
 * <p>Fonction pure : aucune dépendance, aucun appel réseau, pour être éprouvée directement.</p>
 *
 * <h2>Ce qui est traduit</h2>
 * <ul>
 *   <li><b>Les fourchettes calculées pour l'athlète</b> ({@code Workout.calculatedPaces}) : chaque
 *       bloc porte une allure min/max en s/km, convertie en vitesse (m/s) comme l'exige Garmin
 *       pour une cible {@code PACE}. À défaut d'allure, la fourchette cardio ; à défaut, aucune
 *       cible ({@code OPEN}).</li>
 *   <li><b>Les répétitions</b> : un bloc « 10 × 400 m r 1 min » devient un
 *       {@code WorkoutRepeatStep} contenant l'effort et sa récupération. Les séries
 *       (« 2 × 5 × 400 ») sont déroulées, avec la récupération entre séries.</li>
 *   <li><b>Les blocs enchaînés</b> (« 8 × (200 m / 400 m) ») : chaque étape garde sa cible.</li>
 * </ul>
 *
 * <p>Séance sans calcul (ancienne structure par zones) : les étapes sont envoyées sans cible
 * chiffrée, la zone en description. Séance sans structure : un seul pas, sur la distance ou la
 * durée prévue.</p>
 */
public final class GarminWorkoutMapper {

    /** Limite Garmin d'un entraînement mono-sport. */
    public static final int MAX_STEPS = 100;
    /** Nom du fournisseur affiché à l'athlète (20 caractères au plus). */
    public static final String PROVIDER = "Darilab";

    private GarminWorkoutMapper() {
    }

    /** Entrée du mapper : ce qu'une séance porte d'utile à Garmin. */
    public record Source(
            String title,
            String notes,
            CalculatedSessionResponse calculated,
            List<WorkoutStep> steps,
            Integer targetDistanceM,
            Integer targetDurationS) {
    }

    /**
     * Construit l'entraînement Garmin.
     *
     * @throws IllegalArgumentException si la séance dépasse la limite de pas de Garmin
     */
    public static Map<String, Object> toGarmin(Source source) {
        List<Map<String, Object>> steps = buildSteps(source);
        int count = count(steps);
        if (count > MAX_STEPS) {
            throw new IllegalArgumentException("Séance trop longue pour Garmin : " + count
                    + " étapes (" + MAX_STEPS + " au plus).");
        }
        number(steps, new int[] {0});

        Map<String, Object> segment = new LinkedHashMap<>();
        segment.put("segmentOrder", 1);
        segment.put("sport", "RUNNING");
        segment.put("steps", steps);

        Map<String, Object> workout = new LinkedHashMap<>();
        workout.put("workoutName", truncate(blankToNull(source.title()) != null ? source.title() : "Séance", 100));
        String notes = blankToNull(source.notes());
        if (notes != null) {
            workout.put("description", truncate(notes, 1024));
        }
        workout.put("sport", "RUNNING");
        workout.put("workoutProvider", PROVIDER);
        workout.put("workoutSourceId", PROVIDER);
        workout.put("segments", List.of(segment));
        return workout;
    }

    // --- Construction -------------------------------------------------------------------------

    private static List<Map<String, Object>> buildSteps(Source s) {
        List<Map<String, Object>> out = new ArrayList<>();
        CalculatedSessionResponse calc = s.calculated();
        if (calc != null && hasBlocks(calc)) {
            addEntries(out, calc.warmup(), "WARMUP");
            addEntries(out, calc.main(), null);
            addEntries(out, calc.cooldown(), "COOLDOWN");
            if (!out.isEmpty()) {
                return out;
            }
        }
        if (s.steps() != null && !s.steps().isEmpty()) {
            for (WorkoutStep st : s.steps()) {
                out.addAll(fromLegacyStep(st));
            }
            return out;
        }
        // Séance sans structure : un seul pas, sur ce qui est prévu.
        out.add(step("ACTIVE", duration(s.targetDistanceM(), s.targetDurationS(), null), null, null));
        return out;
    }

    private static boolean hasBlocks(CalculatedSessionResponse c) {
        return !(isEmpty(c.warmup()) && isEmpty(c.main()) && isEmpty(c.cooldown()));
    }

    private static void addEntries(List<Map<String, Object>> out, List<CalculatedBlockEntry> entries,
                                   String forcedIntensity) {
        if (entries == null) {
            return;
        }
        for (CalculatedBlockEntry e : entries) {
            if (e == null || e.block() == null) {
                continue;
            }
            addBlock(out, e, forcedIntensity);
        }
    }

    private static void addBlock(List<Map<String, Object>> out, CalculatedBlockEntry e, String forcedIntensity) {
        CourseBlock b = e.block();
        int reps = b.repCount();
        int sets = b.setCount();

        Supplier<List<Map<String, Object>>> unit;
        if (b.isChain()) {
            unit = () -> {
                List<Map<String, Object>> u = new ArrayList<>();
                for (CalculatedStepEntry se : e.steps()) {
                    CourseStep st = se.step();
                    if (st == null) {
                        continue;
                    }
                    u.add(effort(forcedIntensity != null ? forcedIntensity : "INTERVAL",
                            st.distanceM(), st.durationS(), se.calc(), st.note()));
                    if (st.recovery() != null) {
                        u.add(recovery(st.recovery(), se.recoveryCalc()));
                    }
                }
                return u;
            };
        } else {
            String intensity = forcedIntensity != null ? forcedIntensity
                    : (reps > 1 || sets > 1 ? "INTERVAL" : "ACTIVE");
            unit = () -> {
                List<Map<String, Object>> u = new ArrayList<>();
                u.add(effort(intensity, b.distanceM(), b.durationS(), e.calc(), b.note()));
                if (b.recovery() != null && (reps > 1 || sets > 1)) {
                    u.add(recovery(b.recovery(), e.recoveryCalc()));
                }
                return u;
            };
        }

        for (int set = 1; set <= sets; set++) {
            if (reps > 1) {
                out.add(repeat(reps, unit.get()));
            } else {
                out.addAll(unit.get());
            }
            if (set < sets && b.setRecovery() != null) {
                out.add(recovery(b.setRecovery(), null));
            }
        }
    }

    private static Map<String, Object> effort(String intensity, Integer distanceM, Integer durationS,
                                              CalculatedBlockResponse calc, String note) {
        Integer estimated = calc != null ? calc.estimatedDurationS() : null;
        return step(intensity, duration(distanceM, durationS, estimated), target(calc), note);
    }

    private static Map<String, Object> recovery(CourseRecovery r, CalculatedBlockResponse calc) {
        // « static » : on s'arrête. Le reste (trot, marche) est une récupération active.
        String intensity = "static".equalsIgnoreCase(r.type()) ? "REST" : "RECOVERY";
        return step(intensity, duration(r.distanceM(), r.durationS(), null), target(calc), null);
    }

    private static List<Map<String, Object>> fromLegacyStep(WorkoutStep st) {
        String intensity = st.getStepType() == null ? "ACTIVE" : switch (st.getStepType()) {
            case WARMUP -> "WARMUP";
            case COOLDOWN -> "COOLDOWN";
            case RECOVERY -> "RECOVERY";
            case REPETITION -> "INTERVAL";
            default -> "ACTIVE";
        };
        String desc = st.getZone() != null ? "Zone " + st.getZone().name() : null;
        if (blankToNull(st.getNotes()) != null) {
            desc = desc == null ? st.getNotes() : desc + " — " + st.getNotes();
        }
        Map<String, Object> s = step(intensity, duration(st.getDistanceM(), st.getDurationS(), null), null, desc);
        if (st.getRepetitions() > 1) {
            List<Map<String, Object>> inner = new ArrayList<>();
            inner.add(s);
            return List.of(repeat(st.getRepetitions(), inner));
        }
        return List.of(s);
    }

    // --- Briques Garmin -----------------------------------------------------------------------

    /** Durée d'un pas : distance d'abord (c'est ainsi qu'un coach prescrit), puis temps, sinon touche Lap. */
    private record Duration(String type, Double value) {
    }

    private static Duration duration(Integer distanceM, Integer durationS, Integer estimatedDurationS) {
        if (distanceM != null && distanceM > 0) {
            return new Duration("DISTANCE", distanceM.doubleValue());
        }
        if (durationS != null && durationS > 0) {
            return new Duration("TIME", durationS.doubleValue());
        }
        if (estimatedDurationS != null && estimatedDurationS > 0) {
            return new Duration("TIME", estimatedDurationS.doubleValue());
        }
        return new Duration("OPEN", null);
    }

    /** Cible d'un pas : allure (en m/s), sinon fréquence cardiaque, sinon aucune. */
    private record Target(String type, Double low, Double high) {
    }

    private static Target target(CalculatedBlockResponse c) {
        if (c == null || !c.computable()) {
            return null;
        }
        if (c.paceMinSecPerKm() != null && c.paceMaxSecPerKm() != null
                && c.paceMinSecPerKm() > 0 && c.paceMaxSecPerKm() > 0) {
            // Le plus lent des deux bornes en s/km donne la vitesse basse.
            int slow = Math.max(c.paceMinSecPerKm(), c.paceMaxSecPerKm());
            int fast = Math.min(c.paceMinSecPerKm(), c.paceMaxSecPerKm());
            return new Target("PACE", speed(slow), speed(fast));
        }
        if (c.hrMin() != null && c.hrMax() != null && c.hrMin() > 0 && c.hrMax() > 0) {
            return new Target("HEART_RATE",
                    (double) Math.min(c.hrMin(), c.hrMax()), (double) Math.max(c.hrMin(), c.hrMax()));
        }
        return null;
    }

    /** s/km → m/s, arrondi au millième (au-delà, la montre n'affiche rien de plus). */
    static double speed(int secPerKm) {
        return Math.round(1000.0 / secPerKm * 1000.0) / 1000.0;
    }

    private static Map<String, Object> step(String intensity, Duration d, Target t, String note) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "WorkoutStep");
        s.put("intensity", intensity);
        String desc = blankToNull(note);
        if (desc != null) {
            s.put("description", truncate(desc, 512));
        }
        s.put("durationType", d.type());
        s.put("durationValue", d.value());
        if (t != null) {
            s.put("targetType", t.type());
            s.put("targetValueLow", t.low());
            s.put("targetValueHigh", t.high());
        } else {
            s.put("targetType", "OPEN");
        }
        return s;
    }

    private static Map<String, Object> repeat(int times, List<Map<String, Object>> inner) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("type", "WorkoutRepeatStep");
        r.put("repeatType", "REPEAT_UNTIL_STEPS_CMPLT");
        r.put("repeatValue", (double) times);
        r.put("steps", inner);
        return r;
    }

    /** Numérote tous les pas dans l'ordre de lecture, répétitions comprises. */
    @SuppressWarnings("unchecked")
    static void number(List<Map<String, Object>> steps, int[] counter) {
        for (Map<String, Object> s : steps) {
            s.put("stepOrder", ++counter[0]);
            Object children = s.get("steps");
            if (children instanceof List<?> list) {
                number((List<Map<String, Object>>) list, counter);
            }
        }
    }

    @SuppressWarnings("unchecked")
    static int count(List<Map<String, Object>> steps) {
        int n = 0;
        for (Map<String, Object> s : steps) {
            n++;
            Object children = s.get("steps");
            if (children instanceof List<?> list) {
                n += count((List<Map<String, Object>>) list);
            }
        }
        return n;
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
