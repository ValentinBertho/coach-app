package com.coachrun;

import com.coachrun.dto.response.CalculatedBlockResponse;
import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.dto.response.CalculatedSessionResponse.CalculatedBlockEntry;
import com.coachrun.dto.response.CalculatedSessionResponse.CalculatedStepEntry;
import com.coachrun.dto.session.CourseBlock;
import com.coachrun.dto.session.CourseRecovery;
import com.coachrun.dto.session.CourseStep;
import com.coachrun.entity.WorkoutStep;
import com.coachrun.entity.enums.IntensityZone;
import com.coachrun.entity.enums.WorkoutStepType;
import com.coachrun.integration.GarminWorkoutMapper;
import com.coachrun.integration.GarminWorkoutMapper.Source;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Séance Darilab → entraînement Garmin. Ce qui compte : que la montre affiche la fourchette
 * calculée pour l'athlète, sur la bonne distance, autant de fois que prescrit.
 */
class GarminWorkoutMapperTest {

    /** Allure 4:00–4:10/km : 250 s et 240 s. */
    private static CalculatedBlockResponse pace(int fast, int slow) {
        return new CalculatedBlockResponse(true, null, null, fast, slow, null, null, null, null,
                null, null, null, null, null, null, false);
    }

    private static CalculatedBlockResponse hr(int min, int max) {
        return new CalculatedBlockResponse(true, null, null, null, null, null, null, null, null,
                min, max, null, null, null, null, false);
    }

    private static CourseBlock block(Integer reps, Integer distanceM, Integer durationS,
                                     CourseRecovery recovery, Integer sets, CourseRecovery setRecovery) {
        return new CourseBlock("b", "interval", reps, distanceM, durationS, null, recovery, null, null,
                null, sets, setRecovery, null);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> steps(Map<String, Object> workout) {
        var segment = ((List<Map<String, Object>>) workout.get("segments")).get(0);
        return (List<Map<String, Object>>) segment.get("steps");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> children(Map<String, Object> repeat) {
        return (List<Map<String, Object>>) repeat.get("steps");
    }

    @Test
    void unFractionneDevientUneRepetitionAvecLAllureDeLAthlete() {
        var calc = new CalculatedSessionResponse(
                List.of(new CalculatedBlockEntry(block(1, null, 900, null, null, null), hr(130, 145), null)),
                List.of(new CalculatedBlockEntry(
                        block(10, 400, null, new CourseRecovery("jog", 60, null, null), null, null),
                        pace(80, 84), null)),
                List.of(new CalculatedBlockEntry(block(1, 2000, null, null, null, null), null, null)),
                null, null);

        var workout = GarminWorkoutMapper.toGarmin(new Source("10 × 400", "Bien s'échauffer", calc, List.of(), null, null));

        assertThat(workout.get("sport")).isEqualTo("RUNNING");
        assertThat(workout.get("workoutName")).isEqualTo("10 × 400");
        assertThat(workout.get("workoutProvider")).isEqualTo("Darilab");
        var steps = steps(workout);
        assertThat(steps).hasSize(3);

        var warmup = steps.get(0);
        assertThat(warmup.get("intensity")).isEqualTo("WARMUP");
        assertThat(warmup.get("durationType")).isEqualTo("TIME");
        assertThat(warmup.get("durationValue")).isEqualTo(900.0);
        assertThat(warmup.get("targetType")).isEqualTo("HEART_RATE");
        assertThat(warmup.get("targetValueLow")).isEqualTo(130.0);
        assertThat(warmup.get("targetValueHigh")).isEqualTo(145.0);

        var repeat = steps.get(1);
        assertThat(repeat.get("type")).isEqualTo("WorkoutRepeatStep");
        assertThat(repeat.get("repeatType")).isEqualTo("REPEAT_UNTIL_STEPS_CMPLT");
        assertThat(repeat.get("repeatValue")).isEqualTo(10.0);
        var effort = children(repeat).get(0);
        assertThat(effort.get("intensity")).isEqualTo("INTERVAL");
        assertThat(effort.get("durationType")).isEqualTo("DISTANCE");
        assertThat(effort.get("durationValue")).isEqualTo(400.0);
        assertThat(effort.get("targetType")).isEqualTo("PACE");
        // 84 s/km → 11,905 m/s pour la borne basse, 80 s/km → 12,5 m/s pour la haute.
        assertThat((Double) effort.get("targetValueLow")).isEqualTo(11.905);
        assertThat((Double) effort.get("targetValueHigh")).isEqualTo(12.5);
        var rec = children(repeat).get(1);
        assertThat(rec.get("intensity")).isEqualTo("RECOVERY");
        assertThat(rec.get("durationType")).isEqualTo("TIME");
        assertThat(rec.get("durationValue")).isEqualTo(60.0);
        assertThat(rec.get("targetType")).isEqualTo("OPEN");

        assertThat(steps.get(2).get("intensity")).isEqualTo("COOLDOWN");
        assertThat(steps.get(2).get("targetType")).isEqualTo("OPEN");
    }

    @Test
    void lesPasSontNumerotesDansLOrdreDeLecture() {
        var calc = new CalculatedSessionResponse(List.of(),
                List.of(new CalculatedBlockEntry(
                        block(3, 1000, null, new CourseRecovery("static", 90, null, null), null, null),
                        pace(240, 250), null)),
                List.of(), null, null);
        var steps = steps(GarminWorkoutMapper.toGarmin(new Source("3 × 1000", null, calc, List.of(), null, null)));

        assertThat(steps.get(0).get("stepOrder")).isEqualTo(1);
        assertThat(children(steps.get(0)).get(0).get("stepOrder")).isEqualTo(2);
        assertThat(children(steps.get(0)).get(1).get("stepOrder")).isEqualTo(3);
        assertThat(children(steps.get(0)).get(1).get("intensity")).isEqualTo("REST");
    }

    @Test
    void lesSeriesSontDerouleesAvecLaRecuperationEntreSeries() {
        var calc = new CalculatedSessionResponse(List.of(),
                List.of(new CalculatedBlockEntry(
                        block(5, 400, null, new CourseRecovery("jog", 60, null, null), 2,
                                new CourseRecovery("walk", 300, null, null)),
                        pace(80, 84), null)),
                List.of(), null, null);
        var steps = steps(GarminWorkoutMapper.toGarmin(new Source("2 × 5 × 400", null, calc, List.of(), null, null)));

        assertThat(steps).hasSize(3);
        assertThat(steps.get(0).get("type")).isEqualTo("WorkoutRepeatStep");
        assertThat(steps.get(1).get("durationValue")).isEqualTo(300.0);
        assertThat(steps.get(2).get("type")).isEqualTo("WorkoutRepeatStep");
        // Deux répétitions distinctes, pas deux références à la même : chacune a ses numéros.
        assertThat(children(steps.get(2)).get(0).get("stepOrder"))
                .isNotEqualTo(children(steps.get(0)).get(0).get("stepOrder"));
    }

    @Test
    void unBlocEnchaineGardeLaCibleDeChaqueEtape() {
        var chain = new CourseBlock("c", "chain", 8, null, null, null, null, null, null, null, null, null,
                List.of(new CourseStep("s1", 200, null, null, null, null, null),
                        new CourseStep("s2", 400, null, null, null, null, null)));
        var entry = new CalculatedBlockEntry(chain, null, null, List.of(
                new CalculatedStepEntry(chain.steps().get(0), pace(70, 75), null),
                new CalculatedStepEntry(chain.steps().get(1), pace(90, 95), null)));
        var calc = new CalculatedSessionResponse(List.of(), List.of(entry), List.of(), null, null);

        var steps = steps(GarminWorkoutMapper.toGarmin(new Source("8 × (200/400)", null, calc, List.of(), null, null)));

        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).get("repeatValue")).isEqualTo(8.0);
        var inner = children(steps.get(0));
        assertThat(inner).hasSize(2);
        assertThat(inner.get(0).get("durationValue")).isEqualTo(200.0);
        assertThat((Double) inner.get(0).get("targetValueHigh")).isEqualTo(14.286);
        assertThat(inner.get(1).get("durationValue")).isEqualTo(400.0);
        assertThat((Double) inner.get(1).get("targetValueHigh")).isEqualTo(11.111);
    }

    @Test
    void uneSeanceSansCalculGardeSaStructureSansCible() {
        WorkoutStep warm = new WorkoutStep();
        warm.setStepType(WorkoutStepType.WARMUP);
        warm.setDurationS(600);
        WorkoutStep rep = new WorkoutStep();
        rep.setStepType(WorkoutStepType.REPETITION);
        rep.setRepetitions(6);
        rep.setDistanceM(800);
        rep.setZone(IntensityZone.Z4);

        var steps = steps(GarminWorkoutMapper.toGarmin(new Source("Zones", null, null, List.of(warm, rep), null, null)));

        assertThat(steps.get(0).get("intensity")).isEqualTo("WARMUP");
        assertThat(steps.get(1).get("repeatValue")).isEqualTo(6.0);
        var effort = children(steps.get(1)).get(0);
        assertThat(effort.get("targetType")).isEqualTo("OPEN");
        assertThat(effort.get("description")).isEqualTo("Zone Z4");
    }

    @Test
    void uneSeanceSansStructureDevientUnSeulPas() {
        var steps = steps(GarminWorkoutMapper.toGarmin(new Source("Footing", null, null, List.of(), 10000, null)));
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).get("durationType")).isEqualTo("DISTANCE");
        assertThat(steps.get(0).get("durationValue")).isEqualTo(10000.0);
    }

    @Test
    void uneSeanceTropLonguePourGarminEstRefuseeClairement() {
        List<WorkoutStep> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            WorkoutStep s = new WorkoutStep();
            s.setStepType(WorkoutStepType.STEADY);
            s.setDistanceM(100);
            many.add(s);
        }
        assertThatThrownBy(() -> GarminWorkoutMapper.toGarmin(new Source("Trop", null, null, many, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }
}
