package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Redéfinition du mot de passe via un lien de réinitialisation. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PasswordResetRequest(@NotBlank @Size(min = 8, max = 100) String password) {
}
