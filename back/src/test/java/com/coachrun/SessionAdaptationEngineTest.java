package com.coachrun;

import com.coachrun.dto.session.CourseBlock;
import com.coachrun.dto.session.CoursePrescription;
import com.coachrun.dto.session.SessionStructure;
import com.coachrun.engine.SessionAdaptationEngine;
import com.coachrun.engine.SessionAdaptationEngine.Advice;
import com.coachrun.entity.enums.FormStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le feu vert du matin. Vérifie la hiérarchie des règles — la douleur d'abord, la fatigue ensuite,
 * la charge en dernier — et le fait que l'adaptation produite <b>ne touche pas aux allures</b>.
 */
class SessionAdaptationEngineTest {

    private final SessionAdaptationEngine engine = new SessionAdaptationEngine();

    // --- Recommandation -------------------------------------------------------

    @Test
    void greenWhenNothingIsWrong() {
        var r = engine.recommend(3, 0, 1.0, 9, true);
        assertThat(r.advice()).isEqualTo(Advice.KEEP);
        assertThat(r.severity()).isEqualTo(FormStatus.GREEN);
    }

    @Test
    void nothingToAdviseWithoutASession() {
        var r = engine.recommend(9, 8, 2.0, 9, false);
        assertThat(r.advice()).isEqualTo(Advice.KEEP);
    }

    /** Une douleur ne se traite pas en enlevant des kilomètres, mais en enlevant les allures. */
    @Test
    void painRemovesIntensityRatherThanVolume() {
        var r = engine.recommend(2, 5, 1.0, 5, true);
        assertThat(r.advice()).isEqualTo(Advice.EASY);
        assertThat(r.severity()).isEqualTo(FormStatus.RED);
        assertThat(r.reason()).contains("5/10");
    }

    @Test
    void highPainOrPainOnAHardSessionStopsTheDay() {
        assertThat(engine.recommend(2, 7, 1.0, 4, true).advice()).isEqualTo(Advice.MOVE);
        assertThat(engine.recommend(2, 5, 1.0, 9, true).advice()).isEqualTo(Advice.MOVE);
    }

    /** La fatigue réduit le volume — et les allures restent celles qui étaient prescrites. */
    @Test
    void fatigueLightensVolume() {
        var r = engine.recommend(7, 0, 1.0, 9, true);
        assertThat(r.advice()).isEqualTo(Advice.LIGHTEN);
        assertThat(r.reason()).contains("mêmes allures");
    }

    @Test
    void extremeFatigueOnAHardSessionMovesIt() {
        assertThat(engine.recommend(9, 0, 1.0, 9, true).advice()).isEqualTo(Advice.MOVE);
        // La même fatigue sur un footing ne justifie pas d'annuler : on allège.
        assertThat(engine.recommend(9, 0, 1.0, 3, true).advice()).isEqualTo(Advice.LIGHTEN);
    }

    /** Une charge élevée ne compte que si la séance du jour est elle-même dure. */
    @Test
    void loadOnlyMattersOnAHardSession() {
        assertThat(engine.recommend(2, 0, 1.6, 9, true).advice()).isEqualTo(Advice.LIGHTEN);
        assertThat(engine.recommend(2, 0, 1.6, 3, true).advice()).isEqualTo(Advice.KEEP);
    }

    /** Une gêne déclarée mérite un accusé de réception, pas une modification. */
    @Test
    void mildPainIsAcknowledgedWithoutChangingTheSession() {
        var r = engine.recommend(2, 3, 1.0, 4, true);
        assertThat(r.advice()).isEqualTo(Advice.KEEP);
        assertThat(r.severity()).isEqualTo(FormStatus.ORANGE);
        assertThat(r.reason()).isNotNull();
    }

    /** Le RPE d'une séance passée n'entre jamais dans l'état du jour (invariant DARI Lab). */
    @Test
    void adviceIgnoresEverythingButFatiguePainAndLoad() {
        assertThat(engine.recommend(null, null, null, 10, true).advice()).isEqualTo(Advice.KEEP);
    }

    // --- Fabrication de la séance allégée -------------------------------------

    @Test
    void lighteningCutsRepetitionsAndKeepsPrescription() {
        var rx = CoursePrescription.ofRange(com.coachrun.entity.enums.PrescriptionRef.PCT_LT2, 98d, 103d);
        var structure = new SessionStructure(
                List.of(block("warmup", 1, null, 900, null)),
                List.of(block("intervals", 6, 1000, null, rx)),
                List.of(block("cooldown", 1, null, 600, null)));

        var lightened = engine.lighten(structure);

        assertThat(lightened.main()).hasSize(1);
        assertThat(lightened.main().get(0).reps()).isEqualTo(4);
        assertThat(lightened.main().get(0).distanceM()).isEqualTo(1000);
        // L'allure prescrite est intacte : c'est tout le sens d'un allègement.
        assertThat(lightened.main().get(0).prescription()).isEqualTo(rx);
        // Échauffement et retour au calme ne bougent pas : les raccourcir n'allège rien.
        assertThat(lightened.warmup()).isEqualTo(structure.warmup());
        assertThat(lightened.cooldown()).isEqualTo(structure.cooldown());
    }

    @Test
    void lighteningAlwaysRemovesAtLeastOneRepetition() {
        var structure = new SessionStructure(List.of(),
                List.of(block("intervals", 2, 400, null, null)), List.of());
        assertThat(engine.lighten(structure).main().get(0).reps()).isEqualTo(1);
    }

    @Test
    void lighteningAContinuousRunShortensIt() {
        var structure = new SessionStructure(List.of(),
                List.of(block("easy", 1, null, 3600, null)), List.of());
        assertThat(engine.lighten(structure).main().get(0).durationS()).isEqualTo(2400);
    }

    /** Transformée en footing : un seul bloc, à l'allure de l'échauffement. */
    @Test
    void easyReplacesTheMainSectionWithASingleBlock() {
        var warmupRx = CoursePrescription.ofRange(
                com.coachrun.entity.enums.PrescriptionRef.PCT_LT1, 90d, 95d);
        var structure = new SessionStructure(
                List.of(block("warmup", 1, null, 900, warmupRx)),
                List.of(block("intervals", 6, 1000, null,
                        CoursePrescription.ofRange(com.coachrun.entity.enums.PrescriptionRef.PCT_LT2, 98d, 103d))),
                List.of(block("cooldown", 1, null, 600, null)));

        var easy = engine.easy(structure, 1800);

        assertThat(easy.main()).hasSize(1);
        assertThat(easy.main().get(0).durationS()).isEqualTo(1800);
        assertThat(easy.main().get(0).prescription()).isEqualTo(warmupRx);
        assertThat(easy.main().get(0).rpe()).isEqualTo(3);
        assertThat(easy.warmup()).isEqualTo(structure.warmup());
    }

    private CourseBlock block(String type, Integer reps, Integer distanceM, Integer durationS,
                              CoursePrescription rx) {
        return new CourseBlock(UUID.randomUUID().toString(), type, reps, distanceM, durationS,
                rx, null, null, null, List.of(), 1, null);
    }
}
