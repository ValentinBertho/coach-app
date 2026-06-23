package com.coachrun.engine;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Détection des seuils lactiques à partir des paliers d'un test (cf. DARI Lab).
 * <ul>
 *   <li><strong>LT1</strong> = vitesse à laquelle le lactate atteint le lactate de repos + 0,5 mmol/L.</li>
 *   <li><strong>LT2</strong> = méthode <strong>Dmax modifié</strong> : droite reliant le point LT1 au
 *       dernier palier, LT2 = palier à distance perpendiculaire maximale de cette droite.</li>
 * </ul>
 * Logique pure (testable sans contexte Spring).
 */
@Component
public class LactateThresholdEngine {

    /** Palier de test : vitesse (m/s), lactate (mmol/L) et FC optionnelle. */
    public record StepPoint(double speedMs, Double lactate, Integer hr) {
    }

    /** Résultat de détection (valeurs nulles si paliers insuffisants). */
    public record LTDetectionResult(
            Double baseline, Double lt1Threshold,
            Double lt1Ms, Double lt2Ms,
            Integer fcLt1, Integer fcLt2) {
    }

    public LTDetectionResult detect(List<StepPoint> rawSteps, Double lactateRest) {
        List<StepPoint> steps = rawSteps.stream()
                .filter(s -> s.speedMs() > 0 && s.lactate() != null)
                .sorted(Comparator.comparingDouble(StepPoint::speedMs))
                .toList();

        if (steps.size() < 3) {
            Double baseline = lactateRest != null ? lactateRest
                    : (steps.isEmpty() ? null : steps.get(0).lactate());
            Double threshold = baseline == null ? null : baseline + 0.5;
            return new LTDetectionResult(baseline, threshold, null, null, null, null);
        }

        double baseline = lactateRest != null ? lactateRest : steps.get(0).lactate();
        double lt1Threshold = baseline + 0.5;

        double lt1Ms = speedAtLactate(steps, lt1Threshold);
        double lt2Ms = dmaxModified(steps, lt1Ms, lt1Threshold);

        Integer fcLt1 = hrAtSpeed(steps, lt1Ms);
        Integer fcLt2 = hrAtSpeed(steps, lt2Ms);

        return new LTDetectionResult(round(baseline), round(lt1Threshold),
                round(lt1Ms), round(lt2Ms), fcLt1, fcLt2);
    }

    // --- Interpolations -------------------------------------------------------

    /** Vitesse (interpolée) à laquelle le lactate atteint {@code target}. */
    private double speedAtLactate(List<StepPoint> steps, double target) {
        if (target <= steps.get(0).lactate()) {
            return steps.get(0).speedMs();
        }
        for (int i = 1; i < steps.size(); i++) {
            double prev = steps.get(i - 1).lactate();
            double cur = steps.get(i).lactate();
            if (cur >= target && cur != prev) {
                double frac = (target - prev) / (cur - prev);
                frac = Math.max(0, Math.min(1, frac));
                return steps.get(i - 1).speedMs()
                        + frac * (steps.get(i).speedMs() - steps.get(i - 1).speedMs());
            }
        }
        return steps.get(steps.size() - 1).speedMs();
    }

    /** Dmax modifié : palier à distance perpendiculaire max de la droite (LT1 → dernier palier). */
    private double dmaxModified(List<StepPoint> steps, double lt1Ms, double lt1Lactate) {
        StepPoint last = steps.get(steps.size() - 1);
        double ax = lt1Ms;
        double ay = lt1Lactate;
        double bx = last.speedMs();
        double by = last.lactate();
        double abLen = Math.hypot(bx - ax, by - ay);
        if (abLen == 0) {
            return lt1Ms;
        }

        double maxDist = -1;
        double lt2Speed = lt1Ms;
        for (StepPoint p : steps) {
            if (p.speedMs() < lt1Ms) {
                continue;
            }
            double cross = Math.abs((bx - ax) * (p.lactate() - ay) - (by - ay) * (p.speedMs() - ax));
            double dist = cross / abLen;
            if (dist > maxDist) {
                maxDist = dist;
                lt2Speed = p.speedMs();
            }
        }
        return lt2Speed;
    }

    /** FC (interpolée) à une vitesse donnée, ou {null} si FC absente des paliers. */
    private Integer hrAtSpeed(List<StepPoint> steps, double targetSpeed) {
        if (targetSpeed <= steps.get(0).speedMs()) {
            return steps.get(0).hr();
        }
        for (int i = 1; i < steps.size(); i++) {
            double prev = steps.get(i - 1).speedMs();
            double cur = steps.get(i).speedMs();
            if (targetSpeed <= cur && cur != prev) {
                Integer hrPrev = steps.get(i - 1).hr();
                Integer hrCur = steps.get(i).hr();
                if (hrPrev == null || hrCur == null) {
                    return hrCur != null ? hrCur : hrPrev;
                }
                double frac = (targetSpeed - prev) / (cur - prev);
                return (int) Math.round(hrPrev + frac * (hrCur - hrPrev));
            }
        }
        return steps.get(steps.size() - 1).hr();
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
