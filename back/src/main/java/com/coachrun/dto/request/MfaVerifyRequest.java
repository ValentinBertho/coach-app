package com.coachrun.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Code de l'application d'authentification (6 chiffres) ou code de secours. */
public record MfaVerifyRequest(
        @NotBlank @Size(max = 128) String challengeToken,
        @NotBlank @Size(max = 32) String code) {
}
