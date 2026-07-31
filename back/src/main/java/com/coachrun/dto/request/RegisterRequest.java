package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inscription d'un coach : crée son compte (HEAD_COACH) et son club implicite.
 * L'acceptation des CGU / politique de confidentialité est exigée (RGPD).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(max = 120) String clubName,
        @jakarta.validation.constraints.AssertTrue(message = "L'acceptation des conditions d'utilisation est requise.")
        boolean termsAccepted) {
}
