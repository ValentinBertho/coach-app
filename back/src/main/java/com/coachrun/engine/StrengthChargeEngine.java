package com.coachrun.engine;

import com.coachrun.dto.response.ChargeTarget;
import com.coachrun.dto.strength.StrengthPrescription;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Calculateur de charge de force (cf. DARI Lab) : dérive la charge cible en kg d'un exercice
 * prescrit à partir du référentiel (kg fixe/fourchette, %RM, RM cible) et du 1RM de l'athlète,
 * arrondie au palier de 2,5 kg. Construit aussi le libellé d'effort (RPE/RIR). Logique pure.
 */
@Component
public class StrengthChargeEngine {

    private final OneRmEngine oneRmEngine;

    public StrengthChargeEngine(OneRmEngine oneRmEngine) {
        this.oneRmEngine = oneRmEngine;
    }

    public ChargeTarget resolve(StrengthPrescription p, Double oneRmKg) {
        if (p == null || p.chargeRefType() == null) {
            return notComputable(oneRmKg, effortLabel(p));
        }
        Double min = null;
        Double max = null;

        switch (p.chargeRefType()) {
            case KG_FIXE -> {
                min = p.chargeKgMin();
                max = p.chargeKgMin();
            }
            case KG_RANGE -> {
                min = p.chargeKgMin();
                max = p.chargeKgMax() != null ? p.chargeKgMax() : p.chargeKgMin();
            }
            case PCT_RM -> {
                if (oneRmKg == null || p.chargePctRmMin() == null) {
                    return notComputable(oneRmKg, effortLabel(p));
                }
                min = oneRmEngine.chargeForPct(oneRmKg, p.chargePctRmMin());
                max = min;
            }
            case PCT_RM_RANGE -> {
                if (oneRmKg == null || p.chargePctRmMin() == null || p.chargePctRmMax() == null) {
                    return notComputable(oneRmKg, effortLabel(p));
                }
                min = oneRmEngine.chargeForPct(oneRmKg, p.chargePctRmMin());
                max = oneRmEngine.chargeForPct(oneRmKg, p.chargePctRmMax());
            }
            case RM_CIBLE, RM_ESTIME -> {
                Integer reps = p.repsFixed() != null ? p.repsFixed() : p.repsMin();
                if (oneRmKg == null || reps == null || reps <= 0) {
                    return notComputable(oneRmKg, effortLabel(p));
                }
                min = oneRmEngine.round2_5(oneRmKg * oneRmEngine.nuzzoPct1RM(reps) / 100.0);
                max = min;
            }
            default -> {
                return notComputable(oneRmKg, effortLabel(p));
            }
        }

        if (min == null) {
            return notComputable(oneRmKg, effortLabel(p));
        }
        return new ChargeTarget(true, oneRmKg, min, max, chargeLabel(min, max), effortLabel(p));
    }

    // --- Libellés -------------------------------------------------------------

    private String chargeLabel(double min, double max) {
        if (min == max) {
            return fmt(min) + " kg";
        }
        return fmt(min) + "–" + fmt(max) + " kg";
    }

    private String effortLabel(StrengthPrescription p) {
        if (p == null || p.effortRefType() == null) {
            return null;
        }
        return switch (p.effortRefType()) {
            case RPE -> p.rpeMin() == null ? null : "RPE " + fmt(p.rpeMin());
            case RPE_RANGE -> "RPE " + fmt(p.rpeMin()) + "–" + fmt(p.rpeMax());
            case RIR -> p.rirMin() == null ? null : "RIR " + p.rirMin();
            case RIR_RANGE -> "RIR " + p.rirMin() + "–" + p.rirMax();
        };
    }

    private ChargeTarget notComputable(Double oneRmKg, String effortLabel) {
        return new ChargeTarget(false, oneRmKg, null, null, null, effortLabel);
    }

    private String fmt(double v) {
        if (v == Math.floor(v)) {
            return String.format(Locale.ROOT, "%d", (long) v);
        }
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
