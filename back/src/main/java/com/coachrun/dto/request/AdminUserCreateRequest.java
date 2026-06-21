package com.coachrun.dto.request;

import com.coachrun.entity.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Création d'un utilisateur par l'admin (admin / head coach / coach). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminUserCreateRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 120) String fullName,
        @NotNull UserRole role,
        UUID clubId) {
}
