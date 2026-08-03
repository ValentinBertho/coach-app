package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Acceptation d'invitation athlète : consentements (CGU + données de santé) et définition des
 * identifiants de connexion.
 *
 * <p><b>Deux consentements, tous deux exigés côté serveur.</b> La preuve d'un consentement ne peut
 * pas dépendre du bon vouloir du client qui envoie le champ :</p>
 * <ul>
 *   <li>{@code healthDataConsent} — traitement des données de santé (RGPD art. 9) ;</li>
 *   <li>{@code termsAccepted} — CGU. Il manquait : les athlètes, qui sont la moitié des
 *       utilisateurs et les personnes concernées par les données de santé, n'acceptaient
 *       <b>jamais</b> les CGU. Or ce sont elles qui portent l'avertissement santé (« aucun avis
 *       médical ») et la clause de version bêta (disponibilité et conservation non garanties) :
 *       sans acceptation horodatée, aucune des deux ne leur est opposable. Le chemin coach le
 *       faisait déjà, à l'inscription comme à l'invitation.</li>
 * </ul>
 *
 * <p><b>Mot de passe obligatoire.</b> Il était facultatif « par compatibilité », alors que
 * l'acceptation efface le jeton d'invitation dans la foulée : un compte créé sans mot de passe
 * n'avait plus aucune voie d'entrée — la connexion refuse un hachage nul, et la réinitialisation
 * ignore les adresses internes {@code @athlete.coachrun.local}. L'interface l'exigeait déjà ; le
 * verrou ne reposait donc que sur le client, sur le parcours le plus sensible du produit.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InvitationAcceptRequest(
        @NotNull(message = "Le consentement au traitement des données de santé est requis.")
        @AssertTrue(message = "Le consentement au traitement des données de santé est requis.")
        Boolean healthDataConsent,

        @NotNull(message = "L'acceptation des conditions générales d'utilisation est requise.")
        @AssertTrue(message = "L'acceptation des conditions générales d'utilisation est requise.")
        Boolean termsAccepted,

        @NotBlank(message = "Une adresse e-mail est requise pour pouvoir se reconnecter.")
        @jakarta.validation.constraints.Email(message = "Adresse e-mail invalide.")
        String email,

        @NotBlank(message = "Un mot de passe est requis pour pouvoir se reconnecter.")
        @Size(min = 8, max = 100, message = "Le mot de passe doit faire au moins 8 caractères.")
        String password) {
}
