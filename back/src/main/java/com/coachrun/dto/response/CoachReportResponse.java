package com.coachrun.dto.response;

import com.coachrun.entity.CoachProfileReport;
import com.coachrun.entity.enums.CoachReportReason;
import com.coachrun.entity.enums.CoachReportStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Un signalement, vu de la file d'arbitrage. <b>Réservé aux administrateurs plateforme.</b>
 *
 * <p>Le coach signalé ne voit jamais cet objet, et ce n'est pas une commodité : lui montrer les
 * signalements en cours l'exposerait à identifier qui l'a signalé — souvent l'un de ses trois
 * athlètes — bien avant qu'un humain ait établi si le reproche tenait.</p>
 */
public record CoachReportResponse(
        UUID id,
        UUID profileId,
        String coachSlug,
        String coachName,
        CoachReportReason reason,
        String reasonLabel,
        String details,
        CoachReportStatus status,
        String statusLabel,
        Instant createdAt,
        Instant handledAt,
        String moderatorNote,
        /** Vrai si le signalant était connecté : un signalement anonyme ne se pèse pas pareil. */
        boolean fromKnownUser,
        /** Nombre de signalements encore ouverts sur cette même fiche, celui-ci compris. */
        long openReportsOnProfile) {

    public static CoachReportResponse from(CoachProfileReport r, long openReportsOnProfile) {
        return new CoachReportResponse(
                r.getId(),
                r.getProfile().getId(),
                r.getProfile().getSlug(),
                r.getProfile().getCoach().getFullName(),
                r.getReason(),
                r.getReason().label(),
                r.getDetails(),
                r.getStatus(),
                r.getStatus().label(),
                r.getCreatedAt(),
                r.getHandledAt(),
                r.getModeratorNote(),
                r.getReporter() != null,
                openReportsOnProfile);
    }
}
