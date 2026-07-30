package com.coachrun.dto.response;

import com.coachrun.entity.TrainingZone;
import com.coachrun.entity.ZoneMetric;
import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.ZoneAnchor;
import com.coachrun.entity.enums.ZoneModel;
import com.coachrun.entity.enums.ZoneScope;

import java.util.List;
import java.util.UUID;

/**
 * Zone de travail d'un club (liste plate ordonnée par {@code sortOrder}).
 * {@code metricTypeIds} = métriques portées ; {@code rules} = règle de calcul par métrique
 * (ancre + fourchette %, modèle nommé) — chantier zones v2.
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
        List<UUID> metricTypeIds,
        List<Rule> rules
) {

    /** Règle de calcul d'une métrique de la zone. */
    public record Rule(UUID metricTypeId, ZoneAnchor anchor, ZoneAnchor highAnchor,
                       Double lowPct, Double highPct, ZoneModel model) {
    }

    public static TrainingZoneResponse from(TrainingZone z) {
        List<ZoneMetric> ordered = z.getMetrics().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .toList();
        List<UUID> metricIds = ordered.stream().map(zm -> zm.getMetricType().getId()).toList();
        List<Rule> rules = ordered.stream()
                .map(zm -> new Rule(zm.getMetricType().getId(), zm.getAnchor(), zm.getHighAnchor(),
                        zm.getLowPct(), zm.getHighPct(), zm.getModel()))
                .toList();
        return new TrainingZoneResponse(
                z.getId(), z.getName(), z.getColor(), z.getDescription(), z.getSortOrder(),
                z.getScope(), z.getDiscipline(), z.isBuiltin(), metricIds, rules);
    }
}
