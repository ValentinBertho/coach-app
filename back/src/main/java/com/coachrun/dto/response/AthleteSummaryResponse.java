package com.coachrun.dto.response;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.AthleteStatus;

import java.util.UUID;

/** Vue allégée pour les listes d'athlètes (sans données de santé). */
public record AthleteSummaryResponse(
        UUID id,
        String firstName,
        String lastName,
        AthleteLevel level,
        AthleteStatus status,
        boolean invitationPending,
        String groupName,
        /** Le coach courant peut-il prescrire/modifier cet athlète (sinon lecture seule) ? */
        boolean canWrite,
        /** Le suivi est terminé : la fiche reste en lecture chez le coach qui l'a tenue. */
        boolean coachingEnded,
        /** Coup d'œil sur l'entraînement (liste des athlètes du coach), null ailleurs. */
        Glance glance) {

    /**
     * Ce que la carte d'un athlète dit de son entraînement : sa dernière séance, ce qu'il a fait
     * de sa semaine, sa prochaine course. La liste ne montrait qu'un nom, un niveau et un statut :
     * il fallait ouvrir chaque fiche pour savoir qui décrochait.
     */
    public record Glance(
            java.time.LocalDate lastSessionDate,
            String lastSessionStatus,
            int done7,
            int planned7,
            String nextRaceName,
            java.time.LocalDate nextRaceDate) {
    }

    public AthleteSummaryResponse withGlance(Glance g) {
        return new AthleteSummaryResponse(id, firstName, lastName, level, status, invitationPending,
                groupName, canWrite, coachingEnded, g);
    }

    public static AthleteSummaryResponse from(Athlete a) {
        return from(a, true);
    }

    public static AthleteSummaryResponse from(Athlete a, boolean canWrite) {
        return from(a, canWrite, false);
    }

    public static AthleteSummaryResponse from(Athlete a, boolean canWrite, boolean coachingEnded) {
        return new AthleteSummaryResponse(
                a.getId(), a.getFirstName(), a.getLastName(),
                a.getLevel(), a.getStatus(), a.getInviteToken() != null,
                a.getGroup() != null ? a.getGroup().getName() : null,
                canWrite, coachingEnded, null);
    }
}
