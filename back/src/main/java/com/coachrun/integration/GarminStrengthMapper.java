package com.coachrun.integration;

import com.coachrun.dto.response.CalculatedStrengthResponse;
import com.coachrun.dto.response.CalculatedStrengthResponse.CalculatedExercise;
import com.coachrun.dto.response.CalculatedStrengthResponse.CalculatedStrengthBlock;
import com.coachrun.dto.response.ChargeTarget;
import com.coachrun.dto.strength.StrengthBlock;
import com.coachrun.dto.strength.StrengthExerciseItem;
import com.coachrun.dto.strength.StrengthPrescription;
import com.coachrun.dto.strength.StrengthStructure;
import com.coachrun.entity.enums.BlockFormat;
import com.coachrun.entity.enums.BlockType;
import com.coachrun.entity.enums.SideMode;
import com.coachrun.entity.enums.VolumeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Séance de renforcement Darilab → entraînement Garmin (Training API V2, sport
 * {@code STRENGTH_TRAINING}).
 *
 * <p>Fonction pure, comme {@link GarminWorkoutMapper}.</p>
 *
 * <h2>Ce qui est traduit</h2>
 * <ul>
 *   <li><b>Un exercice en plusieurs séries</b> devient une répétition Garmin : l'exercice, puis
 *       le repos prescrit, autant de fois qu'il y a de séries (sans repos après la dernière).</li>
 *   <li><b>Le volume</b> : répétitions ({@code REPS}), durée ({@code TIME}) ou distance. Une
 *       fourchette (« 8-10 ») part sur sa borne haute, le détail restant lisible en
 *       description.</li>
 *   <li><b>La charge</b> en kilos, celle calculée pour l'athlète quand elle existe (à partir de
 *       son 1RM), sinon celle écrite par le coach.</li>
 *   <li><b>Les circuits</b> (circuit, EMOM, AMRAP, for time) : les exercices s'enchaînent dans un
 *       tour, répété le nombre de tours prévu, ou pendant la durée du bloc.</li>
 *   <li><b>L'exercice</b> lui-même, quand {@link GarminExerciseCatalog} le reconnaît : la montre
 *       affiche alors son animation. Sinon le nom du coach, en description.</li>
 * </ul>
 */
public final class GarminStrengthMapper {

    private GarminStrengthMapper() {
    }

    /** Entrée : le titre, et la structure telle que figée pour l'athlète (charges calculées). */
    public record Source(String title, StrengthStructure structure, CalculatedStrengthResponse calculated) {
    }

    /** Un exercice et la charge calculée pour l'athlète (peut être nulle). */
    private record Item(StrengthExerciseItem item, ChargeTarget charge) {
    }

    /**
     * @throws IllegalArgumentException séance vide, ou au-delà de la limite de pas de Garmin
     */
    public static Map<String, Object> toGarmin(Source source) {
        List<Map<String, Object>> steps = new ArrayList<>();
        for (BlockWithItems b : blocks(source)) {
            addBlock(steps, b.block(), b.items());
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Séance de renforcement sans exercice : rien à envoyer.");
        }
        int count = GarminWorkoutMapper.count(steps);
        if (count > GarminWorkoutMapper.MAX_STEPS) {
            throw new IllegalArgumentException("Séance trop longue pour Garmin : " + count
                    + " étapes (" + GarminWorkoutMapper.MAX_STEPS + " au plus).");
        }
        GarminWorkoutMapper.number(steps, new int[] {0});

        Map<String, Object> segment = new LinkedHashMap<>();
        segment.put("segmentOrder", 1);
        segment.put("sport", "STRENGTH_TRAINING");
        segment.put("steps", steps);

        Map<String, Object> workout = new LinkedHashMap<>();
        String title = source.title() == null || source.title().isBlank() ? "Renforcement" : source.title().trim();
        workout.put("workoutName", truncate(title, 100));
        workout.put("sport", "STRENGTH_TRAINING");
        workout.put("workoutProvider", GarminWorkoutMapper.PROVIDER);
        workout.put("workoutSourceId", GarminWorkoutMapper.PROVIDER);
        workout.put("segments", List.of(segment));
        return workout;
    }

    private record BlockWithItems(StrengthBlock block, List<Item> items) {
    }

    /** Les blocs calculés pour l'athlète s'il y en a (ils portent les charges), sinon la structure. */
    private static List<BlockWithItems> blocks(Source s) {
        List<BlockWithItems> out = new ArrayList<>();
        if (s.calculated() != null && s.calculated().blocks() != null && !s.calculated().blocks().isEmpty()) {
            for (CalculatedStrengthBlock cb : s.calculated().blocks()) {
                if (cb == null || cb.block() == null) {
                    continue;
                }
                List<Item> items = new ArrayList<>();
                if (cb.exercises() != null && !cb.exercises().isEmpty()) {
                    for (CalculatedExercise ce : cb.exercises()) {
                        if (ce != null && ce.item() != null) {
                            items.add(new Item(ce.item(), ce.charge()));
                        }
                    }
                } else {
                    cb.block().exercises().forEach(i -> items.add(new Item(i, null)));
                }
                out.add(new BlockWithItems(cb.block(), items));
            }
            return out;
        }
        if (s.structure() != null) {
            for (StrengthBlock b : s.structure().blocks()) {
                if (b != null) {
                    out.add(new BlockWithItems(b, b.exercises().stream().map(i -> new Item(i, null)).toList()));
                }
            }
        }
        return out;
    }

    private static void addBlock(List<Map<String, Object>> out, StrengthBlock block, List<Item> items) {
        if (items.isEmpty()) {
            return;
        }
        String intensity = intensity(block.blockType());
        if (isCircuit(block)) {
            // Un tour : chaque exercice une fois, puis le repos du bloc.
            List<Map<String, Object>> round = new ArrayList<>();
            for (Item it : items) {
                round.add(exerciseStep(it, intensity, block.workSec()));
            }
            if (block.restSec() != null && block.restSec() > 0) {
                round.add(rest(block.restSec()));
            }
            if (block.rounds() != null && block.rounds() > 1) {
                out.add(repeat("REPEAT_UNTIL_STEPS_CMPLT", block.rounds(), round));
            } else if (block.durationSec() != null && block.durationSec() > 0) {
                out.add(repeat("REPEAT_UNTIL_TIME", block.durationSec(), round));
            } else {
                out.addAll(round);
            }
            return;
        }
        for (Item it : items) {
            StrengthPrescription p = it.item().prescription();
            int sets = p != null && p.sets() != null && p.sets() > 0 ? p.sets() : 1;
            Integer restSec = p == null ? null
                    : p.restSecMax() != null ? p.restSecMax() : p.restSecMin();
            Map<String, Object> exercise = exerciseStep(it, intensity, null);
            if (sets == 1) {
                out.add(exercise);
                continue;
            }
            List<Map<String, Object>> inner = new ArrayList<>();
            inner.add(exercise);
            if (restSec != null && restSec > 0) {
                inner.add(rest(restSec));
            }
            Map<String, Object> r = repeat("REPEAT_UNTIL_STEPS_CMPLT", sets, inner);
            r.put("skipLastRestStep", true);
            out.add(r);
        }
    }

    private static boolean isCircuit(StrengthBlock b) {
        BlockFormat f = b.format();
        return f == BlockFormat.CIRCUIT || f == BlockFormat.EMOM
                || f == BlockFormat.AMRAP || f == BlockFormat.FOR_TIME;
    }

    private static String intensity(BlockType type) {
        if (type == BlockType.ECHAUFFEMENT || type == BlockType.ACTIVATION) {
            return "WARMUP";
        }
        return type == BlockType.CALME ? "COOLDOWN" : "ACTIVE";
    }

    private static Map<String, Object> exerciseStep(Item it, String intensity, Integer blockWorkSec) {
        StrengthExerciseItem item = it.item();
        StrengthPrescription p = item.prescription();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "WorkoutStep");
        s.put("intensity", intensity);
        s.put("description", truncate(describe(it), 512));

        VolumeType volume = p == null ? null : p.effectiveVolumeType();
        Integer reps = p == null ? null : firstNonNull(p.repsFixed(), p.repsMax(), p.repsMin());
        Integer seconds = p == null ? null : firstNonNull(p.durationSec(), p.durationSecMax());
        Integer meters = p == null ? null : firstNonNull(p.distanceM(), p.distanceMMax());
        if (volume == VolumeType.DUREE && seconds != null && seconds > 0) {
            put(s, "TIME", seconds);
        } else if (volume == VolumeType.DISTANCE && meters != null && meters > 0) {
            put(s, "DISTANCE", meters);
        } else if (reps != null && reps > 0) {
            put(s, "REPS", reps);
        } else if (blockWorkSec != null && blockWorkSec > 0) {
            put(s, "TIME", blockWorkSec); // EMOM, tabata : le temps de travail du bloc
        } else {
            s.put("durationType", "OPEN");
            s.put("durationValue", null);
        }
        s.put("targetType", "OPEN");

        Optional<GarminExerciseCatalog.Exercise> ex = GarminExerciseCatalog.match(item.exerciseName());
        s.put("exerciseCategory", ex.map(GarminExerciseCatalog.Exercise::category).orElse(null));
        s.put("exerciseName", ex.map(GarminExerciseCatalog.Exercise::name).orElse(null));
        Double kg = weight(it);
        if (kg != null) {
            s.put("weightValue", kg);
            s.put("weightDisplayUnit", "KILOGRAM");
        }
        return s;
    }

    private static void put(Map<String, Object> s, String type, int value) {
        s.put("durationType", type);
        s.put("durationValue", (double) value);
    }

    /**
     * Charge en kilos : celle calculée pour l'athlète, sinon celle prescrite. Une fourchette part
     * sur son milieu, arrondi au demi-kilo (les disques ne font pas moins).
     */
    private static Double weight(Item it) {
        Double min = null;
        Double max = null;
        if (it.charge() != null && it.charge().computable()) {
            min = it.charge().kgMin();
            max = it.charge().kgMax();
        }
        StrengthPrescription p = it.item().prescription();
        if (min == null && max == null && p != null) {
            min = p.chargeKgMin();
            max = p.chargeKgMax();
        }
        Double kg;
        if (min != null && max != null) {
            kg = (min + max) / 2;
        } else {
            kg = min != null ? min : max;
        }
        if (kg == null || kg <= 0) {
            return null;
        }
        return Math.round(kg * 2) / 2.0;
    }

    /** « Fente bulgare · 8-10 rép. par côté · RPE 7-8 · tempo 3010 · note du coach ». */
    private static String describe(Item it) {
        StrengthExerciseItem item = it.item();
        StrengthPrescription p = item.prescription();
        List<String> parts = new ArrayList<>();
        parts.add(item.exerciseName() == null || item.exerciseName().isBlank() ? "Exercice" : item.exerciseName().trim());
        if (p != null) {
            String reps = range(p.repsMin(), p.repsMax(), p.repsFixed());
            if (reps != null && p.effectiveVolumeType() == VolumeType.REPS) {
                parts.add(reps + " rép." + (p.sideMode() == SideMode.PAR_COTE ? " par côté" : ""));
            } else if (p.sideMode() == SideMode.PAR_COTE) {
                parts.add("par côté");
            }
            String rpe = rangeD(p.rpeMin(), p.rpeMax());
            if (rpe != null) {
                parts.add("RPE " + rpe);
            }
            String rir = range(p.rirMin(), p.rirMax(), null);
            if (rir != null) {
                parts.add("RIR " + rir);
            }
            if (p.tempo() != null && !p.tempo().isBlank()) {
                parts.add("tempo " + p.tempo().trim());
            }
        }
        if (it.charge() != null && it.charge().chargeLabel() != null && !it.charge().chargeLabel().isBlank()) {
            parts.add(it.charge().chargeLabel().trim());
        }
        if (item.coachNotes() != null && !item.coachNotes().isBlank()) {
            parts.add(item.coachNotes().trim());
        }
        return String.join(" · ", parts);
    }

    private static String range(Integer min, Integer max, Integer fixed) {
        if (fixed != null) {
            return String.valueOf(fixed);
        }
        if (min != null && max != null && !min.equals(max)) {
            return min + "-" + max;
        }
        Integer v = min != null ? min : max;
        return v == null ? null : String.valueOf(v);
    }

    private static String rangeD(Double min, Double max) {
        if (min == null && max == null) {
            return null;
        }
        if (min != null && max != null && !min.equals(max)) {
            return fmt(min) + "-" + fmt(max);
        }
        return fmt(min != null ? min : max);
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.FRANCE, "%.1f", d);
    }

    private static Map<String, Object> rest(int seconds) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "WorkoutStep");
        s.put("intensity", "REST");
        s.put("durationType", "TIME");
        s.put("durationValue", (double) seconds);
        s.put("targetType", "OPEN");
        return s;
    }

    private static Map<String, Object> repeat(String type, int value, List<Map<String, Object>> inner) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("type", "WorkoutRepeatStep");
        r.put("repeatType", type);
        r.put("repeatValue", (double) value);
        r.put("steps", inner);
        return r;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
