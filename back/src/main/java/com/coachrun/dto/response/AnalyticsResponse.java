package com.coachrun.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Données agrégées (prêtes à tracer) pour les graphes de charge d'un athlète :
 * volume hebdo prévu/réalisé, répartition par zone, et adhérence (statuts).
 */
public record AnalyticsResponse(
        List<WeekPoint> weeklyVolume,
        Map<String, Integer> zoneDistribution,
        Map<String, Integer> statusCounts) {

    public record WeekPoint(LocalDate weekStart, double plannedKm, double realizedKm) {
    }
}
