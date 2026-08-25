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
 * sans sa date d'expiration ne dit pas s'il faut la relancer, et un athlète sans coach n'était
 * repérable nulle part.</p>
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
        int coachCount) {

    public static AdminAthleteResponse from(Athlete a) {
        return new AdminAthleteResponse(
                a.getId(), a.getFirstName(), a.getLastName(), a.getEmail(),
                a.getClub().getId(), a.getClub().getName(),
                a.getLevel(), a.getStatus(), a.getInviteToken() != null, a.getCreatedAt(),
                a.getInviteExpiresAt(),
                a.getCoaches() != null ? a.getCoaches().size() : 0);
    }
}
