package com.coachrun;

import com.coachrun.engine.IntensityDomainEngine;
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

    private final SessionCalculatorEngine engine =
            new SessionCalculatorEngine(new IntensityDomainEngine());

    /** Profil type : LT1 3.5 / LT2 3.9 m/s, FC 148/163/178, allure 5 km = 4:07 (247 s/km). */
    private AthletePaceContext ctx() {
        return new AthletePaceContext(
                3.5, 3.9, 4.2,
                148, 163, 178,
                80, 90,
                null, null, null, 247, null, null, null, null);
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
    void heartRateNullWhenNoFcAnchors() {
        AthletePaceContext noFc = new AthletePaceContext(
                3.5, 3.9, 4.2, null, null, null, 80, 90,
                null, null, null, 247, null, null, null, null);
        PrescriptionInput in = new PrescriptionInput(PrescriptionRef.PCT_PACE_5KM, 98, 103, 6, 1000, null);
        Result r = engine.calculate(in, noFc);

        assertThat(r.computable()).isTrue();         // l'allure reste calculable
        assertThat(r.hrMin()).isNull();
        assertThat(r.hrMax()).isNull();
    }
}
