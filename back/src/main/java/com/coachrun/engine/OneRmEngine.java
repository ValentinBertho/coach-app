package com.coachrun.engine;

import com.coachrun.entity.enums.RmFormula;
import com.coachrun.entity.enums.StrengthTestProtocol;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Estimation du 1RM (cf. DARI Lab). Quatre formules ; <strong>NUZZO (2024)</strong> par défaut
 * (polynôme degré 3, méthode Pr. Lacourpaille — constante 104.9). Dérive aussi la charge cible
 * (arrondie au palier de 2,5 kg) et les zones de travail Lacourpaille. Logique pure.
 */
@Component
public class OneRmEngine {

    /** Table %1RM selon les répétitions « effectives » (reps réalisées + RIR). */
    private static final Map<Integer, Double> RIR_PCT_TABLE = Map.ofEntries(
            Map.entry(1, 100.0), Map.entry(2, 95.5), Map.entry(3, 92.2), Map.entry(4, 89.2),
            Map.entry(5, 86.3), Map.entry(6, 83.7), Map.entry(7, 81.1), Map.entry(8, 78.6),
            Map.entry(9, 76.2), Map.entry(10, 73.9), Map.entry(11, 71.6), Map.entry(12, 69.4));

    /** Zone de travail dérivée du 1RM (méthode Lacourpaille). */
    public record WorkZone(String name, double pctMin, double pctMax, double kgMin, double kgMax) {
    }

    // --- Conversion RPE ↔ RIR -------------------------------------------------

    public int rirFromRpe(double rpe) {
        return (int) Math.round(10 - rpe);
    }

    // --- Estimation du 1RM ----------------------------------------------------

    /** %1RM en fonction des répétitions (polynôme Nuzzo, constante 104.9). */
    public double nuzzoPct1RM(int reps) {
        return -0.0002 * Math.pow(reps, 3) + 0.0363 * Math.pow(reps, 2) - 2.7814 * reps + 104.9;
    }

    public double nuzzo1RM(double weight, int reps) {
        return weight * 100.0 / nuzzoPct1RM(reps);
    }

    public double epley1RM(double weight, int reps) {
        return reps == 1 ? weight : weight * (1 + reps / 30.0);
    }

    public double brzycki1RM(double weight, int reps) {
        return reps == 1 ? weight : weight * (36.0 / (37 - reps));
    }

    public double rirBased1RM(double weight, int repsDone, int rir) {
        int effective = repsDone + rir;
        double pct = RIR_PCT_TABLE.getOrDefault(Math.min(effective, 12), 65.0);
        return weight / (pct / 100.0);
    }

    /** Estime le e1RM selon la formule choisie ({@code rir} requis pour {@code RIR_BASED}). */
    public double estimate(double weight, int reps, Integer rir, RmFormula formula) {
        return switch (formula) {
            case NUZZO -> nuzzo1RM(weight, reps);
            case EPLEY -> epley1RM(weight, reps);
            case BRZYCKI -> brzycki1RM(weight, reps);
            case RIR_BASED -> rirBased1RM(weight, reps, rir == null ? 0 : rir);
        };
    }

    /**
     * Dérive le e1RM d'un test selon son protocole (cf. DARI Lab §6.5).
     * <ul>
     *   <li>{@code TRUE_1RM} et {@code ISO_MVC} : la charge mesurée est le 1RM.</li>
     *   <li>{@code REP_TEST_3_5} et {@code AMRAP_TEST} : estimation Nuzzo sur les reps réalisées.</li>
     * </ul>
     */
    public double e1rmForTest(StrengthTestProtocol protocol, double weight, Integer reps) {
        return switch (protocol) {
            case TRUE_1RM, ISO_MVC -> weight;
            case REP_TEST_3_5, AMRAP_TEST -> nuzzo1RM(weight, reps == null || reps < 1 ? 1 : reps);
        };
    }

    // --- Charge cible & zones -------------------------------------------------

    /** Charge cible pour un %RM, arrondie au palier de 2,5 kg. */
    public double chargeForPct(double oneRm, double pct) {
        return round2_5(oneRm * pct / 100.0);
    }

    public double round2_5(double kg) {
        return Math.round(kg / 2.5) * 2.5;
    }

    /** Quatre zones de travail Lacourpaille dérivées du 1RM. */
    public List<WorkZone> zones(double oneRm) {
        return List.of(
                zone("Puissance-vitesse", 30, 50, oneRm),
                zone("Puissance-force", 50, 70, oneRm),
                zone("Force maximale", 70, 100, oneRm),
                zone("Hypertrophie", 30, 80, oneRm));
    }

    private WorkZone zone(String name, double pctMin, double pctMax, double oneRm) {
        return new WorkZone(name, pctMin, pctMax,
                round2_5(oneRm * pctMin / 100.0), round2_5(oneRm * pctMax / 100.0));
    }
}
