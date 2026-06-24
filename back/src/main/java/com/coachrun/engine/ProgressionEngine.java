package com.coachrun.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Progression automatique et alertes coach pour la force (cf. DARI Lab §6.7 / §6.8). Logique pure.
 *
 * <p>Progression : si <strong>toutes</strong> les séries sont réalisées <strong>et</strong> RIR &gt;
 * cible <strong>et</strong> douleur ≤ 2 → proposer +2,5 kg (charge &lt; 40 kg) ou +5 kg (≈ +2,5 % RM
 * ou augmentation du volume).</p>
 *
 * <p>Alertes : douleur &gt; 3 en réathlétisation (haute) ; RPE ≥ 9,5 (moyenne) ; RIR 0 alors que
 * 1–2 prescrit (moyenne) ; chute de charge &gt; 15 % vs séance précédente (haute).</p>
 */
@Component
public class ProgressionEngine {

    public enum AlertLevel { HIGH, MEDIUM }

    /** Suggestion de progression pour un exercice. */
    public record Suggestion(boolean recommended, String type, Double deltaKg, Double deltaPct, String label) {
    }

    /** Alerte coach déclenchée par une série réalisée. */
    public record Alert(AlertLevel level, String code, String message) {
    }

    /** Une série réalisée (retour athlète). */
    public record DoneSet(Integer repsDone, Integer rirDone, Double rpeDone, Integer pain, Double chargeKg) {
    }

    /** Suggestion de progression (§6.7). */
    public Suggestion suggest(int targetReps, Integer targetRirMin, List<DoneSet> sets, double currentChargeKg) {
        if (sets.isEmpty()) {
            return new Suggestion(false, "NONE", null, null, "Pas de données");
        }
        boolean allCompleted = sets.stream().allMatch(s -> s.repsDone() != null && s.repsDone() >= targetReps);
        boolean rirAboveTarget = targetRirMin != null
                && sets.stream().allMatch(s -> s.rirDone() != null && s.rirDone() > targetRirMin);
        boolean lowPain = sets.stream().allMatch(s -> s.pain() == null || s.pain() <= 2);

        if (allCompleted && rirAboveTarget && lowPain) {
            double delta = currentChargeKg > 0 && currentChargeKg < 40 ? 2.5 : 5.0;
            String label = "+" + fmt(delta) + " kg (ou +2,5 % RM / +volume)";
            return new Suggestion(true, "CHARGE", delta, 2.5, label);
        }
        return new Suggestion(false, "MAINTAIN", null, null, "Maintenir la charge");
    }

    /** Alertes coach (§6.8). {@code isReath} = exercice de réathlétisation. */
    public List<Alert> alerts(Integer targetRirMin, boolean isReath, List<DoneSet> sets,
                              Double previousChargeKg, double currentChargeKg) {
        List<Alert> out = new ArrayList<>();
        if (sets.isEmpty()) {
            return out;
        }
        int maxPain = sets.stream().filter(s -> s.pain() != null).mapToInt(DoneSet::pain).max().orElse(0);
        double maxRpe = sets.stream().filter(s -> s.rpeDone() != null).mapToDouble(DoneSet::rpeDone).max().orElse(0);
        int minRir = sets.stream().filter(s -> s.rirDone() != null).mapToInt(DoneSet::rirDone).min().orElse(Integer.MAX_VALUE);

        if (isReath && maxPain > 3) {
            out.add(new Alert(AlertLevel.HIGH, "PAIN_REATH", "Douleur " + maxPain + "/10 en réathlétisation"));
        } else if (maxPain >= 5) {
            out.add(new Alert(AlertLevel.HIGH, "PAIN", "Douleur élevée " + maxPain + "/10"));
        }
        if (maxRpe >= 9.5) {
            out.add(new Alert(AlertLevel.MEDIUM, "RPE_HIGH", "RPE " + fmt(maxRpe) + " (proche de l'échec)"));
        }
        if (minRir == 0 && targetRirMin != null && targetRirMin >= 1 && targetRirMin <= 2) {
            out.add(new Alert(AlertLevel.MEDIUM, "RIR_ZERO", "RIR 0 alors que " + targetRirMin + " prescrit"));
        }
        if (previousChargeKg != null && previousChargeKg > 0 && currentChargeKg > 0
                && currentChargeKg < previousChargeKg * 0.85) {
            out.add(new Alert(AlertLevel.HIGH, "CHARGE_DROP", "Chute de charge > 15 % vs séance précédente"));
        }
        return out;
    }

    private String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
