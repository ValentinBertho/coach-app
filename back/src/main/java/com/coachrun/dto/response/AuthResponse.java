package com.coachrun.dto.response;

/** Réponse d'authentification : jetons + profil utilisateur. */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserResponse user) {
}
