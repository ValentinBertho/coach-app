package com.coachrun.dto.response;

import com.coachrun.entity.Athlete;

import java.time.Instant;
import java.util.UUID;

/** Invitation athlète en attente (vue admin). */
public record InvitationAdminResponse(
        UUID athleteId,
        String firstName,
        String lastName,
        String clubName,
        Instant expiresAt) {

    public static InvitationAdminResponse from(Athlete a) {
        return new InvitationAdminResponse(
                a.getId(), a.getFirstName(), a.getLastName(),
                a.getClub().getName(), a.getInviteExpiresAt());
    }
}
