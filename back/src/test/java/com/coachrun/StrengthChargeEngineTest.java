package com.coachrun;

import com.coachrun.dto.response.ChargeTarget;
import com.coachrun.dto.strength.StrengthPrescription;
import com.coachrun.engine.OneRmEngine;
import com.coachrun.engine.StrengthChargeEngine;
import com.coachrun.entity.enums.ChargeRefType;
import com.coachrun.entity.enums.EffortRefType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie le calculateur de charge de force : %RM → kg arrondis au palier 2,5, kg fixe/fourchette,
 * RM cible (Nuzzo), et cas non calculable sans 1RM.
 */
class StrengthChargeEngineTest {

    private final StrengthChargeEngine engine = new StrengthChargeEngine(new OneRmEngine());

    private StrengthPrescription pct(ChargeRefType ref, Double pctMin, Double pctMax) {
        return new StrengthPrescription(ref, null, null, pctMin, pctMax,
                EffortRefType.RIR_RANGE, null, null, 1, 3,
                4, 5, null, null, null, null, "3-1-X-1", 120, 180, null);
    }

    @Test
    void resolvesPctRmRangeRoundedToTwoAndHalf() {
        // 1RM 120 kg, 80–85 % → 96–102 → arrondi 2,5 → 95–102.5.
        ChargeTarget t = engine.resolve(pct(ChargeRefType.PCT_RM_RANGE, 80.0, 85.0), 120.0);
        assertThat(t.computable()).isTrue();
        assertThat(t.kgMin()).isEqualTo(95.0);
        assertThat(t.kgMax()).isEqualTo(102.5);
        assertThat(t.chargeLabel()).isEqualTo("95–102.5 kg");
        assertThat(t.effortLabel()).isEqualTo("RIR 1–3");
    }

    @Test
    void resolvesFixedAndRangeKg() {
        StrengthPrescription fixed = new StrengthPrescription(ChargeRefType.KG_FIXE, 60.0, null,
                null, null, EffortRefType.RPE, 8.0, null, null, null, 3, 8, null, null, null, null, null, 90, 120, null);
        ChargeTarget t = engine.resolve(fixed, null);
        assertThat(t.computable()).isTrue();
        assertThat(t.kgMin()).isEqualTo(60.0);
        assertThat(t.chargeLabel()).isEqualTo("60 kg");
        assertThat(t.effortLabel()).isEqualTo("RPE 8");
    }

    @Test
    void resolvesRmCibleViaNuzzo() {
        // 1RM 100 kg, RM cible 5 reps → 100 × %1RM(5) ≈ 91.9 → arrondi 2,5 → 92.5.
        StrengthPrescription rmc = new StrengthPrescription(ChargeRefType.RM_CIBLE, null, null,
                null, null, EffortRefType.RIR, null, null, 2, null, 4, 5, null, null, null, null, null, 120, 180, null);
        ChargeTarget t = engine.resolve(rmc, 100.0);
        assertThat(t.computable()).isTrue();
        assertThat(t.kgMin()).isEqualTo(92.5);
    }

    @Test
    void notComputableWhenPctButNoOneRm() {
        ChargeTarget t = engine.resolve(pct(ChargeRefType.PCT_RM_RANGE, 80.0, 85.0), null);
        assertThat(t.computable()).isFalse();
        assertThat(t.kgMin()).isNull();
        // Le libellé d'effort reste disponible.
        assertThat(t.effortLabel()).isEqualTo("RIR 1–3");
    }
}
