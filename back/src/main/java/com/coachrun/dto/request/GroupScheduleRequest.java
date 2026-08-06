package com.coachrun.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Planification d'une même séance pour tout un groupe, à une date.
 *
 * <p>Exactement une source : un modèle de séance course ({@code templateId}) <b>ou</b> une séance
 * de préparation physique ({@code strengthSessionId}). Un coach de club prescrit au groupe — c'est
 * le geste de base d'un entraînement collectif — et il devait jusqu'ici répéter la même
 * planification athlète par athlète, ligne par ligne.</p>
 *
 * @param date              jour de la séance
 * @param templateId        modèle de séance course à planifier, ou {@code null}
 * @param strengthSessionId séance de prépa physique à planifier, ou {@code null}
 */
public record GroupScheduleRequest(
        @NotNull LocalDate date,
        UUID templateId,
        UUID strengthSessionId
) {

    /** Une source et une seule : sans ça, la requête ne dit pas quoi planifier. */
    public boolean isValid() {
        return (templateId == null) != (strengthSessionId == null);
    }
}
