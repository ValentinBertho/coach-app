package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

/**
 * L'arbitrage d'une demande de création de club.
 *
 * <p>La note est facultative à la validation, et c'est le motif au refus : elle part telle quelle
 * dans l'e-mail au demandeur. Un refus sans un mot est une porte close sans explication — le
 * candidat redéposera la même demande la semaine suivante.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClubRequestDecision(@Size(max = 1000) String note) {

    /** La note, ou {@code null} si elle est vide : une chaîne blanche n'est pas un motif. */
    public String trimmedNote() {
        return note == null || note.isBlank() ? null : note.trim();
    }
}
