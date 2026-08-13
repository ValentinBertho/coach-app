package com.coachrun.dto.response;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Une séance de course d'un jour donné, vue depuis la journée du coach — tous athlètes de son
 * périmètre confondus.
 *
 * <p>Le calendrier existant se lit <b>athlète par athlète</b> : savoir ce qui est prévu
 * aujourd'hui pour douze athlètes demandait douze ouvertures de fiche, ou un balayage de la
 * grille du mois. Cette ligne est le pendant, à l'échelle du périmètre, de ce que la file de
 * retours fait déjà pour le réalisé.</p>
 *
 * <p>Volontairement pauvre : un nom, un titre, un état, de quoi décider. Le détail de la
 * prescription reste derrière la fiche de séance — on ne prescrit pas depuis une liste.</p>
 */
public record CoachDaySessionResponse(
        UUID workoutId,
        UUID athleteId,
        String athleteName,
        String title,
        /** Type de séance (ENDURANCE, THRESHOLD…) : porte la couleur, pas une décision. */
        String type,
        WorkoutStatus status,
        LocalDate scheduledDate,
        /** L'athlète a déplacé cette séance lui-même — le coach doit pouvoir le voir sans l'ouvrir. */
        boolean movedByAthlete,
        /** Un retour d'athlète attend d'être traité sur cette séance (RPE, douleur, commentaire). */
        boolean awaitingReview
) {

    public static CoachDaySessionResponse from(Workout w, String athleteName) {
        Athlete athlete = w.getAthlete();
        boolean awaiting = w.getCoachReviewedAt() == null
                && (w.getRpe() != null || w.getPain() != null || w.getAthleteComment() != null);
        return new CoachDaySessionResponse(
                w.getId(),
                athlete == null ? null : athlete.getId(),
                athleteName,
                w.getTitle(),
                w.getType() == null ? null : w.getType().name(),
                w.getStatus(),
                w.getScheduledDate(),
                w.isMovedByAthlete(),
                awaiting);
    }
}
