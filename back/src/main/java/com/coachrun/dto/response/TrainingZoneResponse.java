package com.coachrun.dto.response;

import com.coachrun.entity.TrainingZone;
import com.coachrun.entity.ZoneMetric;
import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.ZoneScope;

import java.util.List;
import java.util.UUID;

/**
 * Zone de travail d'un club (liste plate ordonnée par {@code sortOrder}).
 * {@code metricTypeIds} = métriques portées par la zone (résolues contre le catalogue côté front).
 */
public record TrainingZoneResponse(
        UUID id,
        String name,
        String color,
        String description,
        int sortOrder,
        ZoneScope scope,
        Discipline discipline,
        boolean builtin,
        List<UUID> metricTypeIds
) {

    public static TrainingZoneResponse from(TrainingZone z) {
        List<UUID> metricIds = z.getMetrics().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(ZoneMetric::getMetricType)
                .map(com.coachrun.entity.MetricType::getId)
                .toList();
        return new TrainingZoneResponse(
                z.getId(), z.getName(), z.getColor(), z.getDescription(), z.getSortOrder(),
                z.getScope(), z.getDiscipline(), z.isBuiltin(), metricIds);
    }
}
