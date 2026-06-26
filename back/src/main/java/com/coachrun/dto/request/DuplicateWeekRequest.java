package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Duplication d'une semaine de séances course d'un athlète : recopie les séances de la semaine
 * source vers la semaine cible, en conservant le décalage de jour. Les deux dates sont des lundis.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DuplicateWeekRequest(
        @NotNull LocalDate sourceWeekStart,
        @NotNull LocalDate targetWeekStart) {
}
