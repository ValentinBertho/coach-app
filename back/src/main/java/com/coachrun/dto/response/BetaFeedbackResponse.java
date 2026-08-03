package com.coachrun.dto.response;

import com.coachrun.entity.BetaFeedback;
import com.coachrun.entity.enums.FeedbackKind;
import com.coachrun.entity.enums.FeedbackStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Un retour de bêta tel que le back-office plateforme l'affiche.
 *
 * @param authorName nom de l'auteur, ou {@code null} si son compte a été supprimé depuis
 */
public record BetaFeedbackResponse(
        UUID id,
        FeedbackKind kind,
        String message,
        String page,
        String appVersion,
        String userAgent,
        String correlationId,
        FeedbackStatus status,
        String authorName,
        Instant createdAt) {

    public static BetaFeedbackResponse from(BetaFeedback f, String authorName) {
        return new BetaFeedbackResponse(
                f.getId(), f.getKind(), f.getMessage(), f.getPage(), f.getAppVersion(),
                f.getUserAgent(), f.getCorrelationId(), f.getStatus(), authorName, f.getCreatedAt());
    }
}
