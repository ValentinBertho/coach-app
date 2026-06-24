package com.coachrun.dto.request;

import com.coachrun.entity.enums.UnavailabilityReason;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Création/màj d'une indisponibilité athlète. */
public record UnavailabilityRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull UnavailabilityReason reason,
        String notes
) {
}
