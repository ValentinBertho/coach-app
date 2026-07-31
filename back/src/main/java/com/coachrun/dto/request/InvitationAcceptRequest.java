package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

/**
 * Acceptation d'invitation athlète : consentement santé (RGPD art. 9) + définition d'un identifiant
 * de connexion ({@code email} + {@code password}) pour pouvoir se reconnecter ensuite. Les deux sont
 * optionnels (compatibilité : sans mot de passe, l'accès reste par lien magique uniquement) — mais
 * un mot de passe fourni doit respecter la même longueur qu'à l'inscription : sans contrainte, un
 * athlète pouvait s'ouvrir un compte avec « a ».
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InvitationAcceptRequest(
        boolean healthDataConsent,
        String email,
        @Size(min = 8, max = 100) String password) {
}
