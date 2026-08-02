package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Acceptation d'invitation athlète : consentement santé (RGPD art. 9) + définition d'un identifiant
 * de connexion ({@code email} + {@code password}) pour pouvoir se reconnecter ensuite. E-mail et
 * mot de passe restent optionnels (compatibilité : sans mot de passe, l'accès reste par lien
 * magique uniquement) — mais un mot de passe fourni doit respecter la même longueur qu'à
 * l'inscription : sans contrainte, un athlète pouvait s'ouvrir un compte avec « a ».
 *
 * <p>Le consentement santé, lui, est <b>exigé</b> : la preuve d'un consentement art. 9 ne peut pas
 * dépendre du bon vouloir du client qui envoie le champ. Même règle que le
 * {@code termsAccepted} de {@link CoachInvitationAcceptRequest} pour les CGU.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InvitationAcceptRequest(
        @NotNull(message = "Le consentement au traitement des données de santé est requis.")
        @AssertTrue(message = "Le consentement au traitement des données de santé est requis.")
        Boolean healthDataConsent,
        String email,
        @Size(min = 8, max = 100) String password) {
}
