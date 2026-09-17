package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Copier une séance de renforcement <b>déjà planifiée</b> — celle d'un autre athlète comprise —
 * sur le calendrier de l'athlète de l'URL.
 *
 * <p>Pendant exact de {@link WorkoutCopyFromRequest} côté force, et pour la même raison : la
 * cible est l'athlète de la route, celui qu'on écrit et donc celui que doit désigner
 * {@code canWrite} ; la source n'est que lue.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrengthCopyFromRequest(
        /** Séance de force planifiée à recopier, quel que soit l'athlète du club à qui elle est. */
        @NotNull UUID sourceScheduledId,
        @NotNull LocalDate scheduledDate) {
}
