package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Réordonnancement des séances d'un même jour (glisser-déposer intra-jour du calendrier). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutReorderRequest(
        @NotNull LocalDate date,
        @NotNull List<UUID> orderedIds) {
}
