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
        /**
         * Sensation générale déclarée (1 = excellente … 5 = très mauvaise), séance course
         * uniquement — le débrief de force ne la collecte pas encore. Distincte du RPE : une
         * séance peut être très dure et très bien vécue, et c'est ce que le coach cherchait
         * jusqu'ici dans le commentaire libre, quand il y en avait un.
         */
        Integer feel,
        /**
         * Blessures nommées au débrief. Elles déclenchaient une notification immédiate, mais
         * n'apparaissaient nulle part dans l'écran où le coach traite ses retours : le signal
         * partait, la boucle ne se refermait pas.
         */
        java.util.List<com.coachrun.dto.InjuryReport> injuries,
        String comment
) {
}
