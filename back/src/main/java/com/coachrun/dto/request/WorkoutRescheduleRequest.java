package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Replanification d'une séance (glisser-déposer du calendrier). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutRescheduleRequest(@NotNull LocalDate scheduledDate) {
}
