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

    /**
     * Fenêtre chronique de référence (jours). Sert aussi d'échelle de progression affichée à
     * l'athlète et au coach tant que le ratio n'est pas calculable.
     */
    public static final int CHRONIC_WINDOW_DAYS = 28;

    /**
     * Historique minimal avant de publier un ACWR. Sous ce seuil, la charge chronique
     * ({@code total28 / 4}) est divisée par quatre semaines dont l'athlète n'en a vécu qu'une ou
     * deux : le ratio sort mécaniquement autour de 4,0 et déclenche une alerte rouge « risque de
     * blessure » sur un athlète qui vient simplement de commencer.
     */
    public static final int RATIO_MIN_HISTORY_DAYS = 21;

    /** Nombre minimal de séances sur 28 jours avant de publier un ACWR. */
    public static final int RATIO_MIN_SESSIONS_28D = 8;

    /** Charge d'une séance : date, charge sRPE, et RPE (pour la répartition par domaine). */
    public record SessionLoad(LocalDate date, double load, int rpe) {
    }

    /**
     * @param ratio        ACWR, ou {@code null} tant que l'historique est insuffisant
     *                     (cf. {@link #RATIO_MIN_HISTORY_DAYS} / {@link #RATIO_MIN_SESSIONS_28D})
     * @param historyDays  jours écoulés depuis la première séance chargée, plafonné à
     *                     {@link #CHRONIC_WINDOW_DAYS} — progression à afficher quand
     *                     {@code ratioReady} est faux (« ACWR en construction — 12/28 jours »)
     * @param ratioReady   vrai si l'historique suffit à publier le ratio
     */
    public record LoadMetrics(
            double acuteLoad7d,
            double chronicLoad28dWeekly,
            Double ratio,
            Double monotony,
            double[] domainPct7d,
            double[] domainPct28d,
            int sessions7d,
            int sessions28d,
            int historyDays,
            boolean ratioReady) {
    }

    /**
     * Variante sans date de première séance connue : l'historique est déduit de la plus ancienne
     * séance présente dans la liste. Suffisant pour un calcul isolé, mais un athlète qui reprend
     * après une longue coupure paraîtra « neuf » — préférer
     * {@link #compute(List, LocalDate, LocalDate)} quand la date réelle est disponible.
     */
    public LoadMetrics compute(List<SessionLoad> sessions, LocalDate ref) {
        return compute(sessions, ref, null);
    }

    /**
     * @param firstSessionDate date de la première séance chargée de l'athlète (toute son
     *                         histoire, pas seulement la fenêtre), ou {@code null} si inconnue
     */
    public LoadMetrics compute(List<SessionLoad> sessions, LocalDate ref, LocalDate firstSessionDate) {
        double acute = 0;
        double total28 = 0;
        int sessions7 = 0;
        int sessions28 = 0;
        double[] band7 = new double[3];
        double[] band28 = new double[3];
        double[] daily7 = new double[7];

        LocalDate oldestInWindow = null;

        for (SessionLoad s : sessions) {
            long diff = ChronoUnit.DAYS.between(s.date(), ref);
            if (diff < 0 || diff > 27) {
                continue;
            }
            if (oldestInWindow == null || s.date().isBefore(oldestInWindow)) {
                oldestInWindow = s.date();
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
        Double monotony = monotony(daily7);

        // Progression de l'historique : la date réelle de première séance si on la connaît, sinon
        // la plus ancienne séance de la fenêtre.
        LocalDate firstKnown = firstSessionDate != null ? firstSessionDate : oldestInWindow;
        int historyDays = firstKnown == null ? 0
                : (int) Math.max(0, Math.min(CHRONIC_WINDOW_DAYS,
                        ChronoUnit.DAYS.between(firstKnown, ref)));

        // Sans historique suffisant, le ratio n'a pas de sens : on ne le publie pas du tout plutôt
        // que d'exposer une valeur que le tableau de bord traduirait en alerte rouge.
        boolean ratioReady = chronicWeekly > 0
                && historyDays >= RATIO_MIN_HISTORY_DAYS
                && sessions28 >= RATIO_MIN_SESSIONS_28D;
        Double ratio = ratioReady ? round2(acute / chronicWeekly) : null;

        return new LoadMetrics(round2(acute), round2(chronicWeekly), ratio, monotony,
                toPct(band7), toPct(band28), sessions7, sessions28, historyDays, ratioReady);
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
