package com.coachrun.dto.request;

import com.coachrun.entity.enums.ClubRole;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Ajout d'un coach existant (compte Darilab) à un club, avec son rôle. {@code role} optionnel
 * (défaut : coach assistant côté service).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AddCoachRequest(
        @NotBlank @Email String email,
        ClubRole role,
        /** Requis seulement si le coach n'a pas encore de compte (invitation). */
        String fullName) {
}
