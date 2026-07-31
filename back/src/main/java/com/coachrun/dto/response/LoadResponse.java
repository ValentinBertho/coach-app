package com.coachrun.dto.response;

import com.coachrun.engine.LoadEngine;
import com.coachrun.engine.LoadEngine.LoadMetrics;

/**
 * Charge d'entraînement d'un athlète (cf. DARI Lab — Data/ACWR) : charge aiguë/chronique, ratio,
 * monotonie et répartition par domaine d'intensité, sur 7 et 28 jours.
 *
 * @param ratio          {@code null} tant que l'historique est trop court pour un ACWR fiable
 * @param ratioReady     vrai si le ratio est publiable
 * @param historyDays    jours d'historique accumulés, sur {@code chronicWindowDays}
 * @param chronicWindowDays échelle de la barre de progression (28 jours)
 */
public record LoadResponse(
        double acuteLoad7d,
        double chronicLoad28d,
        Double ratio,
        Double monotony,
        DomainDistribution distribution7d,
        DomainDistribution distribution28d,
        int sessions7d,
        int sessions28d,
        boolean ratioReady,
        int historyDays,
        int chronicWindowDays
) {

    /** Répartition de charge par domaine d'intensité (en %). */
    public record DomainDistribution(double domain1Pct, double domain2Pct, double domain3Pct) {
        static DomainDistribution of(double[] pct) {
            return new DomainDistribution(pct[0], pct[1], pct[2]);
        }
    }

    public static LoadResponse from(LoadMetrics m) {
        return new LoadResponse(
                m.acuteLoad7d(), m.chronicLoad28dWeekly(), m.ratio(), m.monotony(),
                DomainDistribution.of(m.domainPct7d()),
                DomainDistribution.of(m.domainPct28d()),
                m.sessions7d(), m.sessions28d(),
                m.ratioReady(), m.historyDays(), LoadEngine.CHRONIC_WINDOW_DAYS);
    }
}
