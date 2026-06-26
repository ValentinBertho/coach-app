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
        boolean canWrite) {

    public static AthleteSummaryResponse from(Athlete a) {
        return from(a, true);
    }

    public static AthleteSummaryResponse from(Athlete a, boolean canWrite) {
        return new AthleteSummaryResponse(
                a.getId(), a.getFirstName(), a.getLastName(),
                a.getLevel(), a.getStatus(), a.getInviteToken() != null,
                a.getGroup() != null ? a.getGroup().getName() : null,
                canWrite);
    }
}
