package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dépôt d'une demande de création de club, depuis la page publique d'inscription.
 *
 * <p>Aucun mot de passe : ce formulaire ne crée pas de compte. Le coach choisira le sien au
 * premier lien reçu, si sa demande est validée — c'est aussi ce qui prouve qu'il est bien le
 * titulaire de l'adresse déposée.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClubCreationRequestSubmission(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 120) String fullName,
        /** Nom de la structure ; facultatif pour un indépendant (cf. {@code soloPractice}). */
        @Size(max = 120) String clubName,
        /** Le candidat exerce en indépendant : la validation ouvrira un espace solo. */
        boolean soloPractice,
        @Size(max = 40) String phone,
        @Size(max = 2000) String message,
        @AssertTrue(message = "L'acceptation des conditions d'utilisation est requise.")
        boolean termsAccepted) {
}
