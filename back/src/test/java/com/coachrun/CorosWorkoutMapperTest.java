package com.coachrun;

import com.coachrun.dto.response.CalculatedBlockResponse;
import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.dto.response.CalculatedSessionResponse.CalculatedBlockEntry;
import com.coachrun.dto.session.CourseBlock;
import com.coachrun.dto.session.CourseRecovery;
import com.coachrun.integration.CorosWorkoutMapper;
import com.coachrun.integration.CorosWorkoutMapper.Vocabulary;
import com.coachrun.integration.GarminWorkoutMapper.Source;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Séance Darilab → {@code course} COROS : sections échauffement / travail / retour au calme,
 * répétitions déroulées, allure en s/km, et toute la prescription lisible en description.
 */
class CorosWorkoutMapperTest {

    private static final Vocabulary V = Vocabulary.DEFAULT;

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
    private static List<Map<String, Object>> sections(Map<String, Object> course) {
        return (List<Map<String, Object>>) course.get("sections");
    }

    @Test
    void unFractionneDevientDesSectionsAvecLAllureDeLAthlete() {
        var calc = new CalculatedSessionResponse(
                List.of(new CalculatedBlockEntry(block(1, 2000, null, null, null, null), null, null)),
                List.of(new CalculatedBlockEntry(
                        block(3, 400, null, new CourseRecovery("jog", 60, null, null), null, null),
                        pace(225, 235), null)),
                List.of(new CalculatedBlockEntry(block(1, 1500, null, null, null, null), null, null)),
                null, null);

        var course = CorosWorkoutMapper.toCourse(new Source("3 × 400", "Bien s'échauffer", calc, List.of(), null, null), V);

        assertThat(course.get("sportType")).isEqualTo(1);
        assertThat(course.get("courseName")).isEqualTo("3 × 400");
        var s = sections(course);
        // échauffement + 3 × (400 + récup) + retour au calme ; la récup de 60 s sans code « durée »
        // connu n'a pas de distance : elle n'est pas posée sur la montre mais reste en description.
        assertThat(s).hasSize(5);
        assertThat(s.get(0)).containsEntry("sectionType", 1).containsEntry("targetType", 1)
                .containsEntry("targetValue", 2000).doesNotContainKey("intensityType");
        for (int i = 1; i <= 3; i++) {
            assertThat(s.get(i)).containsEntry("sectionType", 2).containsEntry("targetValue", 400)
                    .containsEntry("intensityType", 2)
                    .containsEntry("intensityValueStart", 225).containsEntry("intensityValueEnd", 235);
        }
        assertThat(s.get(4)).containsEntry("sectionType", 4).containsEntry("targetValue", 1500);

        String desc = (String) course.get("courseDescription");
        assertThat(desc).startsWith("Bien s'échauffer");
        assertThat(desc).contains("3 × 400 m à 3:45–3:55/km + récup 1 min").contains("Envoyée par Darilab.");
    }

    @Test
    void avecLesCodesDuSchemaLaRecuperationEnTempsEtLaFcSontPosees() throws Exception {
        String schema = """
                {"type":"object","properties":{"course":{"type":"object","properties":{
                  "sportType":{"type":"integer","description":"1=Run, 2=Bike, 5=Trail Run"},
                  "sections":{"type":"array","items":{"type":"object","properties":{
                    "sectionType":{"type":"integer","description":"1=Warm Up, 2=Training, 3=Rest, 4=Cool Down"},
                    "targetType":{"type":"integer","description":"1=Distance, 2=Time"},
                    "intensityType":{"type":"integer","description":"1=Heart Rate, 2=Pace"}
                  }}}}}}}
                """;
        Vocabulary v = Vocabulary.fromSchema(new ObjectMapper().readTree(schema));
        assertThat(v.sectionRecovery()).isEqualTo(3);
        assertThat(v.targetTime()).isEqualTo(2);
        assertThat(v.intensityHeartRate()).isEqualTo(1);
        assertThat(v.intensityPace()).isEqualTo(2);

        var calc = new CalculatedSessionResponse(
                List.of(new CalculatedBlockEntry(block(1, null, 900, null, null, null), hr(130, 145), null)),
                List.of(new CalculatedBlockEntry(
                        block(2, 1000, null, new CourseRecovery("jog", 90, null, null), null, null),
                        pace(240, 250), null)),
                List.of(), null, null);
        var s = sections(CorosWorkoutMapper.toCourse(new Source("Seuil", null, calc, List.of(), null, null), v));

        assertThat(s).hasSize(5);
        assertThat(s.get(0)).containsEntry("sectionType", 1).containsEntry("targetType", 2)
                .containsEntry("targetValue", 900).containsEntry("intensityType", 1)
                .containsEntry("intensityValueStart", 130).containsEntry("intensityValueEnd", 145);
        assertThat(s.get(2)).containsEntry("sectionType", 3).containsEntry("targetType", 2)
                .containsEntry("targetValue", 90);
    }

    @Test
    void lesSeriesSontDerouleesAvecLaRecuperationEntreSeries() {
        var calc = new CalculatedSessionResponse(List.of(),
                List.of(new CalculatedBlockEntry(
                        block(2, 200, null, null, 2, new CourseRecovery("walk", null, 400, null)),
                        pace(200, 210), null)),
                List.of(), null, null);
        var s = sections(CorosWorkoutMapper.toCourse(new Source("2 × 2 × 200", null, calc, List.of(), null, null), V));

        // série 1 : 200, 200 ; récup 400 m ; série 2 : 200, 200
        assertThat(s).extracting(m -> m.get("targetValue")).containsExactly(200, 200, 400, 200, 200);
    }

    @Test
    void uneSeanceSansBlocUtiliseLaDistanceCible() {
        var s = sections(CorosWorkoutMapper.toCourse(new Source("Footing", null, null, List.of(), 10000, null), V));
        assertThat(s).hasSize(1);
        assertThat(s.get(0)).containsEntry("sectionType", 2).containsEntry("targetValue", 10000);
    }

    @Test
    void uneSeanceSansDistanceNiDureeEstRefusee() {
        assertThatThrownBy(() -> CorosWorkoutMapper.toCourse(new Source("Libre", null, null, List.of(), null, null), V))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laFormeSimpleEstUneSeuleSectionSurLaDistanceTotale() {
        var calc = new CalculatedSessionResponse(
                List.of(new CalculatedBlockEntry(block(1, 2000, null, null, null, null), null, null)),
                List.of(new CalculatedBlockEntry(block(5, 1000, null, null, null, null), pace(240, 250), null)),
                List.of(), null, null);
        var course = CorosWorkoutMapper.toSimpleCourse(new Source("5 × 1000", null, calc, List.of(), null, null), V);
        var s = sections(course);
        assertThat(s).hasSize(1);
        assertThat(s.get(0)).containsEntry("sectionType", 2).containsEntry("targetValue", 7000)
                .doesNotContainKey("intensityType");
        assertThat((String) course.get("courseDescription")).contains("5 × 1 km");
    }

    @Test
    void uneSeanceRetireeEstReecriteAnnulee() {
        var course = CorosWorkoutMapper.cancelledCourse("Sortie longue", V);
        assertThat(course.get("courseName")).isEqualTo("Annulée par ton coach");
        assertThat((String) course.get("courseDescription")).contains("« Sortie longue »").contains("app COROS");
        assertThat(sections(course)).hasSize(1);
    }

    @Test
    void tropDeSectionsSontRefusees() {
        var calc = new CalculatedSessionResponse(List.of(),
                List.of(new CalculatedBlockEntry(
                        block(30, 200, null, new CourseRecovery("jog", null, 200, null), null, null),
                        pace(200, 210), null)),
                List.of(), null, null);
        assertThatThrownBy(() -> CorosWorkoutMapper.toCourse(new Source("30 × 200", null, calc, List.of(), null, null), V))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trop longue");
    }
}
