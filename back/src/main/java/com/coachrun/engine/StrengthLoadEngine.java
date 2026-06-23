package com.coachrun.engine;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Charge interne d'une séance de force en unités arbitraires (UA), cf. DARI Lab §6.6.
 *
 * <ul>
 *   <li><strong>Charge mécanique</strong> = Σ(charge × reps × %1RM) / 100 — pondère le tonnage
 *       par l'intensité relative. Sans 1RM connu, on retombe sur le tonnage brut (charge × reps).</li>
 *   <li><strong>Charge métabolique</strong> = RPE séance × durée (min) — sRPE de Foster.</li>
 * </ul>
 *
 * Logique pure, sans état ni I/O.
 */
@Component
public class StrengthLoadEngine {

    /** Une série réalisée : charge, reps, 1RM de référence (optionnel), RPE et durée (optionnels). */
    public record SetLoad(double chargeKg, int reps, Double oneRmKg, Integer rpe, Integer durationSec) {
    }

    /** Charge mécanique (UA) = Σ(charge × reps × %1RM)/100, ou tonnage brut si 1RM inconnu. */
    public double mechanicalLoad(List<SetLoad> sets) {
        double total = 0;
        for (SetLoad s : sets) {
            if (s.reps() <= 0 || s.chargeKg() <= 0) {
                continue;
            }
            double tonnage = s.chargeKg() * s.reps();
            if (s.oneRmKg() != null && s.oneRmKg() > 0) {
                double pct = s.chargeKg() / s.oneRmKg() * 100.0;
                total += tonnage * pct / 100.0;
            } else {
                total += tonnage;
            }
        }
        return round1(total);
    }

    /** Charge métabolique (UA) = RPE séance × durée en minutes (sRPE Foster). */
    public double metabolicLoad(Integer sessionRpe, double durationMin) {
        if (sessionRpe == null || sessionRpe <= 0 || durationMin <= 0) {
            return 0;
        }
        return round1(sessionRpe * durationMin);
    }

    /** RPE séance = moyenne (arrondie) des RPE de séries renseignés, ou {@code null} si aucun. */
    public Integer sessionRpe(List<SetLoad> sets) {
        int sum = 0;
        int n = 0;
        for (SetLoad s : sets) {
            if (s.rpe() != null) {
                sum += s.rpe();
                n++;
            }
        }
        return n == 0 ? null : Math.round((float) sum / n);
    }

    /** Durée totale (min) à partir des durées de séries renseignées. */
    public double totalDurationMin(List<SetLoad> sets) {
        int sec = 0;
        for (SetLoad s : sets) {
            if (s.durationSec() != null) {
                sec += s.durationSec();
            }
        }
        return sec / 60.0;
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
