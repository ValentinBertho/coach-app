package com.coachrun.dto.request;

import com.coachrun.entity.enums.FeedbackKind;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Retour envoyé depuis l'application.
 *
 * <p>Seul le message est exigé : demander plus au moment où quelqu'un signale un problème est le
 * meilleur moyen de ne rien recevoir. Le contexte est rempli par le client sans que l'utilisateur
 * ait à y penser, et le navigateur est lu côté serveur dans l'en-tête de la requête.</p>
 *
 * @param kind          bug, idée ou question ({@code BUG} par défaut)
 * @param message       le retour lui-même
 * @param page          chemin de l'écran d'où part le retour
 * @param appVersion    version de l'application affichée au client
 * @param correlationId identifiant de la dernière erreur serveur vue, s'il y en a eu une
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BetaFeedbackRequest(
        FeedbackKind kind,

        @NotBlank(message = "Le message est requis.")
        @Size(max = 4000, message = "Le message ne peut pas dépasser 4000 caractères.")
        String message,

        @Size(max = 512) String page,
        @Size(max = 64) String appVersion,
        @Size(max = 64) String correlationId) {
}
