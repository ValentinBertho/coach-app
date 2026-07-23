package com.coachrun.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Réordonnancement des zones de travail (glisser-déposer de l'écran « Zones & métriques »). */
public record TrainingZoneReorderRequest(
        @NotNull List<UUID> orderedIds) {
}
