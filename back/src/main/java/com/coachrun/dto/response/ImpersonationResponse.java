package com.coachrun.dto.response;

/**
 * Session d'impersonation ouverte par un administrateur : un jeton d'accès pour le compte visé.
 *
 * <p><b>Volontairement distinct de {@link AuthResponse}</b>, et sans jeton de rafraîchissement.
 * Une impersonation n'est pas une connexion : elle dure le temps d'un jeton d'accès, après quoi
 * l'écran ramène l'administrateur sur son propre compte. Émettre un jeton de rafraîchissement en
 * ferait une session de trente jours indistinguable d'une vraie — exactement ce qu'un accès de
 * débogage ne doit pas être.</p>
 *
 * @param user le compte emprunté, tel que l'application le verra — c'est ce que l'écran affiche
 *             dans le bandeau qui rappelle en permanence que la session n'est pas la sienne
 */
public record ImpersonationResponse(
        String accessToken,
        long expiresIn,
        UserResponse user) {
}
