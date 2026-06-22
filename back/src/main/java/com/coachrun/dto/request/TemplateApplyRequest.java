package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** Application d'un modèle au calendrier d'un athlète. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TemplateApplyRequest(@NotNull UUID athleteId, @NotNull LocalDate date) {
}
