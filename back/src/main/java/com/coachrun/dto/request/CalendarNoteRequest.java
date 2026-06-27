package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Note de calendrier (coach) sur une date d'un athlète. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CalendarNoteRequest(
        @NotNull LocalDate noteDate,
        @NotBlank @Size(max = 500) String text) {
}
