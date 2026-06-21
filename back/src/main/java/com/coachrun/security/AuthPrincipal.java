package com.coachrun.security;

import com.coachrun.entity.enums.UserRole;

import java.util.UUID;

/**
 * Identité authentifiée placée dans le SecurityContext (principal). Porte le scoping tenant
 * ({@code clubId}) utilisé par {@link ClubAccessValidator}.
 */
public record AuthPrincipal(UUID userId, UUID clubId, String email, UserRole role) {
}
