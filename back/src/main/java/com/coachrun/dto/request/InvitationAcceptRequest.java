package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Acceptation d'invitation athlète : consentement santé (RGPD art. 9) + définition d'un identifiant
 * de connexion ({@code email} + {@code password}) pour pouvoir se reconnecter ensuite. Les deux sont
 * optionnels (compatibilité : sans mot de passe, l'accès reste par lien magique uniquement).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InvitationAcceptRequest(boolean healthDataConsent, String email, String password) {
}
