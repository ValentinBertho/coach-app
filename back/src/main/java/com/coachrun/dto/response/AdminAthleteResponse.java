package com.coachrun.dto.response;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.AthleteStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Ligne de la liste des athlètes.
 *
 * <p>{@code inviteExpiresAt} et {@code coachCount} sont <b>ajoutés</b> : une invitation en attente
 * sans sa date d'expiration ne dit pas s'il faut la relancer, et un athlète que personne ne suit
 * n'était repérable nulle part.</p>
 */
public record AdminAthleteResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        UUID clubId,
        String clubName,
        AthleteLevel level,
        AthleteStatus status,
        boolean invitationPending,
        Instant createdAt,
        Instant inviteExpiresAt,
        /**
         * Coachs qui suivent réellement cet athlète — les relations actives
         * ({@code CoachAthleteRelation}), pas les rattachements additionnels de
         * {@code athlete_coaches}, qui ne disent rien de l'encadrement effectif.
         */
        int coachCount) {

    public static AdminAthleteResponse from(Athlete a, int coachCount) {
        return new AdminAthleteResponse(
                a.getId(), a.getFirstName(), a.getLastName(), a.getEmail(),
                a.getClub().getId(), a.getClub().getName(),
                a.getLevel(), a.getStatus(), a.getInviteToken() != null, a.getCreatedAt(),
                a.getInviteExpiresAt(),
                coachCount);
    }
}
