package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inscription d'un coach : crée son compte (HEAD_COACH) et son espace de travail.
 * L'acceptation des CGU / politique de confidentialité est exigée (RGPD).
 *
 * <p>{@code invitationCode} n'est exigé qu'en mode d'inscription « invite » (bêta sur cohorte
 * fermée) : la validation de sa présence est portée par le service, qui seul connaît le mode
 * actif.</p>
 *
 * <p>{@code clubName} n'est plus {@code @NotBlank}. Il reste obligatoire pour un coach de club —
 * le service le vérifie — mais un indépendant n'a pas de club à nommer, et l'exiger le forçait à
 * en inventer un. Desserrer une validation est sans risque pour les clients déjà déployés ;
 * la resserrer ne l'aurait pas été.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 120) String fullName,
        @Size(max = 120) String clubName,
        /** Le coach exerce en indépendant : pas de club à nommer, et on ne lui en parlera pas. */
        boolean soloPractice,
        @jakarta.validation.constraints.AssertTrue(message = "L'acceptation des conditions d'utilisation est requise.")
        boolean termsAccepted,
        @Size(max = 120) String invitationCode) {
}
