package com.coachrun.dto.request;

import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Mise à jour d'un utilisateur par l'admin (nom, rôle, statut, club). Champs optionnels. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminUserUpdateRequest(
        @Size(max = 120) String fullName,
        UserRole role,
        UserStatus status,
        UUID clubId) {
}
