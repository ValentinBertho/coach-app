package com.coachrun.dto.response;

import java.time.Instant;

/**
 * État du consentement au traitement des données de santé (RGPD art. 9).
 *
 * <p>Exposé aux deux parties, avec la même vérité : à l'athlète pour qu'il sache ce qu'il a
 * accordé et puisse le retirer, au coach pour qu'il sache ce qu'il a le droit de saisir. Le
 * consentement n'était visible de personne — pas même de l'athlète qui l'avait donné.</p>
 *
 * @param granted     vrai si le traitement est actuellement autorisé
 * @param grantedAt   date du consentement en vigueur, {@code null} s'il n'y en a jamais eu
 * @param withdrawnAt date du dernier retrait, {@code null} s'il n'y en a jamais eu
 */
public record HealthConsentResponse(boolean granted, Instant grantedAt, Instant withdrawnAt) {
}
