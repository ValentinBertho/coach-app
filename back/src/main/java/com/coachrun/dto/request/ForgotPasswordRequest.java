package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Demande de réinitialisation de mot de passe. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ForgotPasswordRequest(@NotBlank @Email String email) {
}
