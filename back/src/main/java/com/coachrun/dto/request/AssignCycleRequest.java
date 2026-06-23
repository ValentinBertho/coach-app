package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Assignation d'un cycle au calendrier d'un athlète à partir d'une date de départ. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssignCycleRequest(@NotNull LocalDate startDate) {
}
