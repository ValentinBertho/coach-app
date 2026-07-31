package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Acceptation d'une invitation coach : mot de passe (et nom optionnel). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoachInvitationAcceptRequest(
        @NotBlank @Size(min = 8, max = 100) String password,
        String fullName,
        /* Acceptation des CGU / confidentialité (horodatée). Exigée comme à l'inscription : la
           preuve de consentement RGPD ne peut pas dépendre du client qui envoie le champ. */
        @jakarta.validation.constraints.NotNull(
                message = "L'acceptation des conditions d'utilisation est requise.")
        @jakarta.validation.constraints.AssertTrue(
                message = "L'acceptation des conditions d'utilisation est requise.")
        Boolean termsAccepted) {
}
