package com.coachrun.dto.response;

/**
 * Mot de passe correct, second facteur attendu.
 *
 * @param challengeToken à renvoyer avec le code ; vaut pour cinq minutes et cinq essais
 * @param enrollment     premier passage : {@code secret} et {@code otpauthUri} sont à enregistrer
 *                       dans l'application d'authentification avant de saisir le premier code
 */
public record MfaChallengeResponse(
        String challengeToken,
        boolean enrollment,
        String secret,
        String otpauthUri,
        long expiresIn) {
}
