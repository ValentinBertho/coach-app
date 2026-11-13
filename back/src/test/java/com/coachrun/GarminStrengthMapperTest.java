package com.coachrun;

import com.coachrun.dto.response.CalculatedStrengthResponse;
import com.coachrun.dto.response.ChargeTarget;
import com.coachrun.dto.strength.StrengthBlock;
import com.coachrun.dto.strength.StrengthExerciseItem;
import com.coachrun.dto.strength.StrengthPrescription;
import com.coachrun.dto.strength.StrengthStructure;
import com.coachrun.entity.enums.BlockFormat;
import com.coachrun.entity.enums.BlockType;
import com.coachrun.entity.enums.SideMode;
import com.coachrun.entity.enums.VolumeType;
import com.coachrun.integration.GarminExerciseCatalog;
import com.coachrun.integration.GarminStrengthMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Séance de renforcement → entraînement Garmin {@code STRENGTH_TRAINING}. */
class GarminStrengthMapperTest {

    @Test
    void lesExercicesCourantsSontReconnus() {
        assertThat(GarminExerciseCatalog.match("Fente bulgare")).get()
                .isEqualTo(new GarminExerciseCatalog.Exercise("LUNGE", "DUMBBELL_BULGARIAN_SPLIT_SQUAT"));
        assertThat(GarminExerciseCatalog.match("Soulevé de terre roumain")).get()
                .isEqualTo(new GarminExerciseCatalog.Exercise("DEADLIFT", "ROMANIAN_DEADLIFT"));
        assertThat(GarminExerciseCatalog.match("Squat arrière")).get()
                .isEqualTo(new GarminExerciseCatalog.Exercise("SQUAT", "BARBELL_BACK_SQUAT"));
        assertThat(GarminExerciseCatalog.match("Gainage latéral")).get()
                .isEqualTo(new GarminExerciseCatalog.Exercise("PLANK", "SIDE_PLANK"));
        assertThat(GarminExerciseCatalog.match("Pompes")).get()
                .isEqualTo(new GarminExerciseCatalog.Exercise("PUSH_UP", "PUSH_UP"));
        assertThat(GarminExerciseCatalog.match("Extension mollets debout")).get()
                .isEqualTo(new GarminExerciseCatalog.Exercise("CALF_RAISE", "STANDING_CALF_RAISE"));
        assertThat(GarminExerciseCatalog.match("Swing kettlebell")).get()
                .isEqualTo(new GarminExerciseCatalog.Exercise("HIP_RAISE", "KETTLEBELL_SWING"));
        assertThat(GarminExerciseCatalog.match("Rowing haltère")).get()
                .isEqualTo(new GarminExerciseCatalog.Exercise("ROW", "DUMBBELL_ROW"));
    }

    @Test
    void unExerciceInconnuNestPasDevine() {
        assertThat(GarminExerciseCatalog.match("Toe yoga")).isEmpty();
        assertThat(GarminExerciseCatalog.match("Copenhagen adducteurs")).isEmpty();
        // « row » ne doit pas se lire dans « throw » ni dans « arrow ».
        assertThat(GarminExerciseCatalog.match("Medicine ball throw")).isEmpty();
        assertThat(GarminExerciseCatalog.match(null)).isEmpty();
    }

    @Test
    void desSeriesDeviennentUneRepetitionAvecLeRepos() {
        StrengthStructure structure = new StrengthStructure(List.of(block(BlockType.PRINCIPAL, BlockFormat.CLASSIQUE,
                null, null, item("Squat arrière", reps(4, 6, 6, 120, 90.0, 100.0)))));

        Map<String, Object> w = GarminStrengthMapper.toGarmin(
                new GarminStrengthMapper.Source("Force bas du corps", structure, null));

        assertThat(w.get("sport")).isEqualTo("STRENGTH_TRAINING");
        Map<String, Object> repeat = steps(w).get(0);
        assertThat(repeat.get("type")).isEqualTo("WorkoutRepeatStep");
        assertThat(repeat.get("repeatValue")).isEqualTo(4.0);
        assertThat(repeat.get("skipLastRestStep")).isEqualTo(true);
        List<Map<String, Object>> inner = inner(repeat);
        Map<String, Object> squat = inner.get(0);
        assertThat(squat.get("exerciseCategory")).isEqualTo("SQUAT");
        assertThat(squat.get("exerciseName")).isEqualTo("BARBELL_BACK_SQUAT");
        assertThat(squat.get("durationType")).isEqualTo("REPS");
        assertThat(squat.get("durationValue")).isEqualTo(6.0);
        assertThat(squat.get("weightValue")).isEqualTo(95.0);
        assertThat(squat.get("weightDisplayUnit")).isEqualTo("KILOGRAM");
        assertThat(inner.get(1).get("intensity")).isEqualTo("REST");
        assertThat(inner.get(1).get("durationValue")).isEqualTo(120.0);
    }

    @Test
    void laChargeCalculeePourLAthletePrimeSurCelleEcrite() {
        StrengthExerciseItem squat = item("Squat arrière", reps(3, 5, 5, 90, 60.0, 60.0));
        StrengthBlock b = block(BlockType.PRINCIPAL, BlockFormat.CLASSIQUE, null, null, squat);
        CalculatedStrengthResponse calc = new CalculatedStrengthResponse(List.of(
                new CalculatedStrengthResponse.CalculatedStrengthBlock(b, List.of(
                        new CalculatedStrengthResponse.CalculatedExercise(squat,
                                new ChargeTarget(true, 100.0, 77.3, 82.1, "77-82 kg", null))))));

        Map<String, Object> w = GarminStrengthMapper.toGarmin(new GarminStrengthMapper.Source("S", null, calc));

        assertThat(inner(steps(w).get(0)).get(0).get("weightValue")).as("milieu arrondi au demi-kilo").isEqualTo(79.5);
    }

    @Test
    void unCircuitRepeteSesToursEtUnExerciceAuTempsPartEnTemps() {
        StrengthPrescription plank = new StrengthPrescription(null, null, null, null, null, null, null, null,
                null, null, 1, VolumeType.DUREE, null, null, null, 45, null, null, null, null,
                null, null, null, null, null);
        StrengthStructure structure = new StrengthStructure(List.of(block(BlockType.ACCESSOIRE, BlockFormat.CIRCUIT,
                3, 60, item("Gainage ventral", plank), item("Pompes", reps(1, null, 12, null, null, null)))));

        Map<String, Object> w = GarminStrengthMapper.toGarmin(new GarminStrengthMapper.Source("Circuit", structure, null));

        Map<String, Object> repeat = steps(w).get(0);
        assertThat(repeat.get("repeatValue")).isEqualTo(3.0);
        List<Map<String, Object>> round = inner(repeat);
        assertThat(round).hasSize(3);
        assertThat(round.get(0).get("durationType")).isEqualTo("TIME");
        assertThat(round.get(0).get("durationValue")).isEqualTo(45.0);
        assertThat(round.get(1).get("durationType")).isEqualTo("REPS");
        assertThat(round.get(2).get("intensity")).isEqualTo("REST");
    }

    @Test
    void unExerciceNonReconnuGardeSonNomEtSesConsignes() {
        StrengthPrescription p = new StrengthPrescription(null, null, null, null, null, null, 7.0, 8.0,
                null, null, 1, VolumeType.REPS, null, 8, 10, null, null, null, null, null,
                SideMode.PAR_COTE, "3010", null, null, null);
        StrengthStructure structure = new StrengthStructure(List.of(block(BlockType.ECHAUFFEMENT, BlockFormat.CLASSIQUE,
                null, null, item("Copenhagen adducteurs", p))));

        Map<String, Object> step = steps(GarminStrengthMapper.toGarmin(
                new GarminStrengthMapper.Source("S", structure, null))).get(0);

        assertThat(step.get("exerciseCategory")).isNull();
        assertThat(step.get("intensity")).isEqualTo("WARMUP");
        assertThat(step.get("durationValue")).as("borne haute de la fourchette").isEqualTo(10.0);
        assertThat((String) step.get("description"))
                .contains("Copenhagen adducteurs").contains("8-10 rép. par côté").contains("RPE 7-8").contains("tempo 3010");
    }

    @Test
    void uneSeanceVideNePartPas() {
        assertThatThrownBy(() -> GarminStrengthMapper.toGarmin(
                new GarminStrengthMapper.Source("Vide", StrengthStructure.empty(), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Outils ---

    private static StrengthPrescription reps(int sets, Integer repsMin, Integer repsFixed, Integer rest,
                                             Double kgMin, Double kgMax) {
        return new StrengthPrescription(null, kgMin, kgMax, null, null, null, null, null, null, null,
                sets, VolumeType.REPS, repsFixed, repsMin, null, null, null, null, null, null,
                null, null, rest, rest, null);
    }

    private static StrengthExerciseItem item(String name, StrengthPrescription p) {
        return new StrengthExerciseItem(null, name, null, p, null, null);
    }

    private static StrengthBlock block(BlockType type, BlockFormat format, Integer rounds, Integer restSec,
                                       StrengthExerciseItem... items) {
        return new StrengthBlock("b", type, format, null, rounds, null, restSec, List.of(items));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> steps(Map<String, Object> w) {
        List<Map<String, Object>> segments = (List<Map<String, Object>>) w.get("segments");
        return (List<Map<String, Object>>) segments.get(0).get("steps");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> inner(Map<String, Object> repeat) {
        return (List<Map<String, Object>>) repeat.get("steps");
    }
}
