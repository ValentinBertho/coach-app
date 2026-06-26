package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** Application d'un plan à tous les athlètes actifs d'un groupe à partir d'une date de départ. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanApplyGroupRequest(@NotNull UUID groupId, @NotNull LocalDate startDate) {
}
