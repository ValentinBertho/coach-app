package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** Application d'un plan à un athlète à partir d'une date de départ (lundi de la semaine 1). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanApplyRequest(@NotNull UUID athleteId, @NotNull LocalDate startDate) {
}
