package com.coachrun.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Une ligne de la file « retours à traiter » : une séance réalisée dont l'athlète a laissé un
 * retour (RPE, douleur, commentaire) que le coach n'a pas encore marqué comme traité.
 *
 * <p>Course et force unifiées ({@code kind}) : le coach traite une seule file, pas deux.</p>
 */
public record FeedbackQueueItemResponse(
        /** COURSE (séance course) ou STRENGTH (séance de renforcement). */
        String kind,
        /** Identifiant de la séance course, ou de la séance de force planifiée. */
        UUID sessionId,
        UUID athleteId,
        String athleteName,
        String title,
        LocalDate sessionDate,
        Double rpe,
        Integer fatigue,
        Integer pain,
        String comment
) {
}
