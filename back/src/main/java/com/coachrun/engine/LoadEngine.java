package com.coachrun.engine;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Charge d'entraînement (cf. DARI Lab — méthode sRPE de Foster). Charge de séance = RPE × durée
 * (min), course <strong>et</strong> force confondues. Calcule ATL (7 j), CTL (28 j ramené à la
 * semaine), ratio ACWR, monotonie, et la répartition de charge par domaine d'intensité (proxy RPE).
 * Logique pure (testable sans contexte Spring).
 */
@Component
public class LoadEngine {

    /** Charge d'une séance : date, charge sRPE, et RPE (pour la répartition par domaine). */
    public record SessionLoad(LocalDate date, double load, int rpe) {
    }

    public record LoadMetrics(
            double acuteLoad7d,
            double chronicLoad28dWeekly,
            Double ratio,
            Double monotony,
            double[] domainPct7d,
            double[] domainPct28d,
            int sessions7d,
            int sessions28d) {
    }

    public LoadMetrics compute(List<SessionLoad> sessions, LocalDate ref) {
        double acute = 0;
        double total28 = 0;
        int sessions7 = 0;
        int sessions28 = 0;
        double[] band7 = new double[3];
        double[] band28 = new double[3];
        double[] daily7 = new double[7];

        for (SessionLoad s : sessions) {
            long diff = ChronoUnit.DAYS.between(s.date(), ref);
            if (diff < 0 || diff > 27) {
                continue;
            }
            int band = band(s.rpe());
            total28 += s.load();
            band28[band] += s.load();
            sessions28++;
            if (diff <= 6) {
                acute += s.load();
                band7[band] += s.load();
                sessions7++;
                daily7[(int) diff] += s.load();
            }
        }

        double chronicWeekly = total28 / 4.0;
        Double ratio = chronicWeekly > 0 ? round2(acute / chronicWeekly) : null;
        Double monotony = monotony(daily7);

        return new LoadMetrics(round2(acute), round2(chronicWeekly), ratio, monotony,
                toPct(band7), toPct(band28), sessions7, sessions28);
    }

    // --- Helpers --------------------------------------------------------------

    /** Domaine d'intensité approché par le RPE de séance : ≤4 = D1, ≤7 = D2, sinon D3. */
    private int band(int rpe) {
        if (rpe <= 4) {
            return 0;
        }
        if (rpe <= 7) {
            return 1;
        }
        return 2;
    }

    private Double monotony(double[] daily7) {
        double sum = 0;
        for (double d : daily7) {
            sum += d;
        }
        double mean = sum / 7.0;
        double variance = 0;
        for (double d : daily7) {
            variance += (d - mean) * (d - mean);
        }
        variance /= 7.0;
        double sd = Math.sqrt(variance);
        return sd > 0 ? round2(mean / sd) : null;
    }

    private double[] toPct(double[] band) {
        double total = band[0] + band[1] + band[2];
        if (total <= 0) {
            return new double[]{0, 0, 0};
        }
        return new double[]{
                round1(band[0] / total * 100),
                round1(band[1] / total * 100),
                round1(band[2] / total * 100)};
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
