package com.coachrun.dto.request;

import com.coachrun.entity.enums.RunDistance;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Ajout d'une performance de référence (chrono sur une distance), source du calcul VDOT.
 */
public record PerformanceRequest(
        @NotNull RunDistance distance,
        @NotNull @Min(1) @Max(86_400) Integer timeSeconds,
        LocalDate dateSet
) {
}
