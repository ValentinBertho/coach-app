package com.coachrun.dto.response;

import java.util.List;

/**
 * Réponse d'authentification : jetons + profil utilisateur.
 *
 * <p>Pour un compte soumis à la double authentification, la connexion par mot de passe ne
 * renvoie <b>aucun jeton</b> : seulement {@code mfa}, le défi à relever. Les codes de secours
 * n'apparaissent qu'une fois, à la fin du premier enrôlement.</p>
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserResponse user,
        MfaChallengeResponse mfa,
        List<String> recoveryCodes) {

    public AuthResponse(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
        this(accessToken, refreshToken, expiresIn, user, null, null);
    }

    public static AuthResponse challenge(MfaChallengeResponse mfa) {
        return new AuthResponse(null, null, 0, null, mfa, null);
    }

    public AuthResponse withoutRefreshToken() {
        return new AuthResponse(accessToken, null, expiresIn, user, mfa, recoveryCodes);
    }
}
