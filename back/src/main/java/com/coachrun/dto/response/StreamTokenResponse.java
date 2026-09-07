package com.coachrun.dto.response;

/**
 * Jeton à usage unique pour une requête qui ne peut pas porter d'en-tête {@code Authorization}
 * (flux {@code EventSource}, pièce jointe ouverte dans un onglet).
 *
 * @param token      la valeur à placer en paramètre {@code stream_token}
 * @param expiresIn  durée de vie, en secondes — indicative : le jeton est de toute façon brûlé
 *                   dès son premier usage
 */
public record StreamTokenResponse(String token, long expiresIn) {
}
