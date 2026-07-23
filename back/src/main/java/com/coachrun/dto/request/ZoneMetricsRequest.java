package com.coachrun.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Métriques portées par une zone (l'ordre de la liste fixe l'ordre d'affichage des colonnes). */
public record ZoneMetricsRequest(
        @NotNull List<UUID> metricTypeIds) {
}
