package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Item d'un plan : modèle de séance positionné (semaine 0-based × jour 1=lundi..7=dimanche). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanItemDto(
        @Min(0) int weekIndex,
        @Min(1) @Max(7) int dayOfWeek,
        @NotNull UUID templateId) {
}
