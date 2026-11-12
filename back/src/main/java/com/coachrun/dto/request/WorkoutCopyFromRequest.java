package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Copier une séance déjà planifiée — celle d'un <b>autre</b> athlète comprise — sur le calendrier
 * de l'athlète de l'URL.
 *
 * <p>La cible est l'athlète de la route, pas un champ du corps : c'est lui qu'on écrit, donc
 * c'est lui que doit désigner l'autorisation ({@code canWrite}). La source, elle, n'est que lue.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutCopyFromRequest(
        /** Séance à recopier, quel que soit l'athlète du club à qui elle appartient. */
        @NotNull UUID sourceWorkoutId,
        @NotNull LocalDate scheduledDate) {
}
