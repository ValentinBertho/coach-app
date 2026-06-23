package com.coachrun.dto.request;

import com.coachrun.entity.enums.PermissionLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Accorde une permission (read/comment/write) à un coach sur un athlète club, avec expiration optionnelle. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrantPermissionRequest(@NotNull PermissionLevel permission, Instant expiresAt) {
}
