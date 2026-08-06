package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Note de calendrier (coach) sur une date — ou une période — d'un athlète.
 *
 * @param noteDate premier jour couvert
 * @param endDate  dernier jour couvert (inclus), ou {@code null} pour une note d'un seul jour
 * @param text     texte libre
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CalendarNoteRequest(
        @NotNull LocalDate noteDate,
        LocalDate endDate,
        @NotBlank @Size(max = 500) String text) {

    /** Une fin antérieure au début ne décrit aucune période : elle est refusée, pas corrigée. */
    public boolean hasValidRange() {
        return endDate == null || !endDate.isBefore(noteDate);
    }
}
