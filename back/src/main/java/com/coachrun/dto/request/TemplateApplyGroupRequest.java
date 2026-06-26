package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** Application d'un modèle de séance à tous les athlètes actifs d'un groupe, sur une date. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TemplateApplyGroupRequest(@NotNull UUID groupId, @NotNull LocalDate date) {
}
