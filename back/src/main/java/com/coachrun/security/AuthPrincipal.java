package com.coachrun.security;

import com.coachrun.entity.enums.UserRole;

import java.util.UUID;

/**
 * Identité authentifiée placée dans le SecurityContext (principal). Porte le scoping tenant
 * ({@code clubId}) et, pour un compte ATHLETE, son {@code athleteId}.
 */
public record AuthPrincipal(UUID userId, UUID clubId, UUID athleteId, String email, UserRole role) {
}
