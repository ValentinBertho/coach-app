package com.coachrun;

import com.coachrun.engine.SessionCalculatorEngine;
import com.coachrun.engine.SessionCalculatorEngine.AthletePaceContext;
import com.coachrun.engine.SessionCalculatorEngine.PrescriptionInput;
import com.coachrun.engine.SessionCalculatorEngine.Result;
import com.coachrun.entity.enums.PrescriptionRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie le calculateur de séance : fourchettes d'allure/vitesse, FC interpolée sur LT1/LT2,
 * estimation durée/distance, et cas non calculables.
 */
class SessionCalculatorEngineTest {

    private final SessionCalculatorEngine engine = new SessionCalculatorEngine();

    /** Profil type : LT1 3.5 / LT2 3.9 m/s, FC 148/163/178, allure 5 km = 4:07 (247 s/km). */
    private AthletePaceContext ctx() {
        return new AthletePaceContext(
                3.5, 3.9, 4.2,
                148, 163, 178,
                80, 90,
                null, null, null, 247, null, null, null, null, null, null);
    }

    @Test
    void computesPaceSpeedHrForVdotPaceReference() {
        // 6 × 1000 m à 98–103 % de l'allure 5 km.
        PrescriptionInput in = new PrescriptionInput(PrescriptionRef.PCT_PACE_5KM, 98, 103, 6, 1000, null);
        Result r = engine.calculate(in, ctx());

        assertThat(r.computable()).isTrue();
        assertThat(r.basePaceSecPerKm()).isEqualTo(247);
        // % plus élevé ⇒ plus rapide : paceMin (rapide) < paceMax (lent).
        assertThat(r.paceMinSecPerKm()).isLessThan(r.paceMaxSecPerKm());
        assertThat(r.paceMinSecPerKm()).isBetween(236, 244);   // ~240 (4:00)
        assertThat(r.paceMaxSecPerKm()).isBetween(248, 256);   // ~252 (4:12)
        assertThat(r.paceMinLabel()).contains(":");

        // Vitesse : la borne haute correspond à l'allure rapide (~15 km/h).
        assertThat(r.speedMaxKmh()).isGreaterThan(r.speedMinKmh());
        assertThat(r.speedMaxKmh()).isCloseTo(15.0, org.assertj.core.data.Offset.offset(0.4));

        // FC interpolée sur LT1/LT2 : ~165–173 bpm, croissante avec l'intensité.
        assertThat(r.hrMin()).isLessThan(r.hrMax());
        assertThat(r.hrMin()).isBetween(160, 170);
        assertThat(r.hrMax()).isBetween(170, 178);

        // Volume : 6 km de corps de séance.
        assertThat(r.estimatedDistanceM()).isEqualTo(6000);
        assertThat(r.estimatedDurationS()).isBetween(1400, 1560);

        assertThat(r.rpeMin()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void computesFromThresholdReferenceWithoutVdotPaces() {
        // 95–102 % de LT2 (3.9 m/s ⇒ 256 s/km), sans aucune allure VDOT renseignée.
        PrescriptionInput in = new PrescriptionInput(PrescriptionRef.PCT_LT2, 95, 102, null, null, 1200);
        Result r = engine.calculate(in, ctx());

        assertThat(r.computable()).isTrue();
        assertThat(r.basePaceSecPerKm()).isEqualTo(256);
        assertThat(r.paceMinSecPerKm()).isLessThan(r.paceMaxSecPerKm());
        // Bloc en durée : distance estimée déduite de l'allure moyenne.
        assertThat(r.estimatedDurationS()).isEqualTo(1200);
        assertThat(r.estimatedDistanceM()).isGreaterThan(0);
    }

    @Test
    void notComputableWhenReferenceMissingFromProfile() {
        // Référence allure 10 km mais aucune allure VDOT dans le contexte.
        PrescriptionInput in = new PrescriptionInput(PrescriptionRef.PCT_PACE_10KM, 90, 95, null, 5000, null);
        Result r = engine.calculate(in, ctx());
        assertThat(r.computable()).isFalse();
        assertThat(r.paceMinSecPerKm()).isNull();
    }

    @Test
    void thresholdReferenceFallsBackToVdotPacesWhenNoLactateTest() {
        // Aucun seuil mesuré (pas de test lactate) mais des allures VDOT présentes :
        // une prescription en % LT2 doit rester calculable via le repli (≈ allure 10 km).
        AthletePaceContext noLactate = new AthletePaceContext(
                null, null, null,            // LT1/LT2/VC non mesurés
                null, null, null, 80, 90,
                null, null, 230, 245, 260, null, 285, 300, null, null); // 3000=230, 5km=245, 10km=260, semi=285, marathon=300
        PrescriptionInput lt2 = new PrescriptionInput(PrescriptionRef.PCT_LT2, 95, 102, null, null, 1200);
        Result r = engine.calculate(lt2, noLactate);
        assertThat(r.computable()).isTrue();
        assertThat(r.basePaceSecPerKm()).isEqualTo(260);  // repli sur l'allure 10 km

        // LT1 ≈ allure marathon ; VC ≈ allure 3 km.
        assertThat(engine.calculate(new PrescriptionInput(PrescriptionRef.PCT_LT1, 80, 90, null, null, 600), noLactate)
                .basePaceSecPerKm()).isEqualTo(300);
        assertThat(engine.calculate(new PrescriptionInput(PrescriptionRef.PCT_VC, 95, 100, null, 1000, null), noLactate)
                .basePaceSecPerKm()).isEqualTo(230);
    }

    @Test
    void heartRateNullWhenNoFcAnchors() {
        AthletePaceContext noFc = new AthletePaceContext(
                3.5, 3.9, 4.2, null, null, null, 80, 90,
                null, null, null, 247, null, null, null, null, null, null);
        PrescriptionInput in = new PrescriptionInput(PrescriptionRef.PCT_PACE_5KM, 98, 103, 6, 1000, null);
        Result r = engine.calculate(in, noFc);

        assertThat(r.computable()).isTrue();         // l'allure reste calculable
        assertThat(r.hrMin()).isNull();
        assertThat(r.hrMax()).isNull();
    }

    // --- RPE prescrit (V0-01) -------------------------------------------------

    /** Profil sans aucun seuil mesuré : uniquement des allures VDOT et une VMA. */
    private AthletePaceContext ctxNoThresholds() {
        return new AthletePaceContext(
                null, null, null,               // LT1 / LT2 / VC non mesurés
                null, null, null, 80, 90,
                null, null, 230, 245, 260, null, 285, 300, null, 18.0);
    }

    /**
     * Régression du défaut le plus visible du produit : sans test lactate, le RPE affiché à
     * l'athlète tombait sur la bande du domaine 1 (2–4) pour <b>toutes</b> les séances, fractionné
     * de VMA compris. Le coach prescrivait RPE 8, l'athlète lisait « RPE 2–4 ».
     */
    @Test
    void prescribesHardRpeForVmaIntervalsWithoutAnyMeasuredThreshold() {
        PrescriptionInput intervals =
                new PrescriptionInput(PrescriptionRef.PCT_VMA, 103, 107, 10, 400, null);
        Result r = engine.calculate(intervals, ctxNoThresholds());

        assertThat(r.computable()).isTrue();
        assertThat(r.rpeMin()).isEqualTo(7);
        assertThat(r.rpeMax()).isEqualTo(9);
    }

    /** Le même profil doit continuer à produire un RPE facile sur un footing. */
    @Test
    void prescribesEasyRpeForSubThresholdWorkWithoutMeasuredThreshold() {
        PrescriptionInput easy =
                new PrescriptionInput(PrescriptionRef.PCT_LT1, 72, 80, null, null, 2400);
        Result r = engine.calculate(easy, ctxNoThresholds());

        assertThat(r.rpeMin()).isEqualTo(2);
        assertThat(r.rpeMax()).isEqualTo(4);
    }

    /** Travail au seuil : domaine 2, entre les deux frontières. */
    @Test
    void prescribesThresholdRpeAtLt2() {
        Result r = engine.calculate(
                new PrescriptionInput(PrescriptionRef.PCT_LT2, 97, 100, 4, null, 480),
                ctxNoThresholds());

        assertThat(r.rpeMin()).isEqualTo(5);
        assertThat(r.rpeMax()).isEqualTo(7);
    }

    /**
     * Le RPE ne doit plus dépendre du profil : deux athlètes recevant la même prescription lisent
     * la même consigne d'effort perçu — c'est ce qu'un coach attend d'un RPE.
     */
    @Test
    void rpeIsIdenticalWithAndWithoutMeasuredThresholds() {
        PrescriptionInput in = new PrescriptionInput(PrescriptionRef.PCT_VC, 98, 102, 5, 1000, null);

        Result measured = engine.calculate(in, ctx());
        Result estimated = engine.calculate(in, ctxNoThresholds());

        assertThat(measured.rpeMin()).isEqualTo(estimated.rpeMin());
        assertThat(measured.rpeMax()).isEqualTo(estimated.rpeMax());
        assertThat(measured.rpeMin()).isEqualTo(7);
    }

    /** Le domaine suit le pourcentage, pas seulement le référentiel. */
    @Test
    void percentageShiftsTheDomain() {
        assertThat(engine.domainForPrescription(PrescriptionRef.PCT_LT2, 85, 88))
                .isEqualTo(com.coachrun.entity.enums.IntensityDomain.DOMAIN_1);
        assertThat(engine.domainForPrescription(PrescriptionRef.PCT_LT2, 98, 100))
                .isEqualTo(com.coachrun.entity.enums.IntensityDomain.DOMAIN_2);
        assertThat(engine.domainForPrescription(PrescriptionRef.PCT_LT2, 104, 108))
                .isEqualTo(com.coachrun.entity.enums.IntensityDomain.DOMAIN_3);
    }
}
