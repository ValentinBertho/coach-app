package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Assignation d'une séance de bibliothèque au calendrier d'un athlète, à une date. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScheduleSessionRequest(@NotNull LocalDate date) {
}
