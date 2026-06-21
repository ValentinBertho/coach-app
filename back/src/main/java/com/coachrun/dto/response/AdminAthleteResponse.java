package com.coachrun.dto.response;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.AthleteStatus;

import java.time.Instant;
import java.util.UUID;

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
        Instant createdAt) {

    public static AdminAthleteResponse from(Athlete a) {
        return new AdminAthleteResponse(
                a.getId(), a.getFirstName(), a.getLastName(), a.getEmail(),
                a.getClub().getId(), a.getClub().getName(),
                a.getLevel(), a.getStatus(), a.getInviteToken() != null, a.getCreatedAt());
    }
}
