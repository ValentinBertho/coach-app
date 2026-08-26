package com.coachrun.dto.response;

import java.time.Instant;

/**
 * Lien d'invitation régénéré, rendu à l'administrateur.
 *
 * <p><b>Pourquoi l'URL est dans la réponse.</b> L'e-mail peut ne jamais arriver — c'est même le
 * motif de support le plus fréquent — et un athlète peut n'avoir aucune adresse connue. Le lien
 * doit donc pouvoir être transmis à la main. Il est déjà rendu de la même façon au coach qui
 * invite ({@code AthleteInvitationResponse}) : ne pas le rendre ici obligerait à passer par le
 * compte d'un coach du club pour obtenir la même chose.</p>
 *
 * @param url       lien d'acceptation, à usage unique
 * @param expiresAt fin de validité
 * @param emailSent vrai si un e-mail a effectivement pu partir (adresse connue et envoi actif)
 */
public record AdminInvitationLinkResponse(String url, Instant expiresAt, boolean emailSent) {
}
