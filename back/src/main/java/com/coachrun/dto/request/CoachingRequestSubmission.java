package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Ce qu'un athlète envoie à un coach pour lui demander de le suivre. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoachingRequestSubmission(
        @NotBlank @Size(max = 140) String coachSlug,
        /** Formule souhaitée ; facultative — on peut demander sans se prononcer sur le tarif. */
        UUID offerId,
        /**
         * Le mot de l'athlète. Exigé : une demande sans un mot n'apprend rien au coach, et il ne
         * peut alors que refuser ou poser la question qu'on aurait dû anticiper.
         */
        @NotBlank @Size(min = 20, max = 2000) String message) {
}
