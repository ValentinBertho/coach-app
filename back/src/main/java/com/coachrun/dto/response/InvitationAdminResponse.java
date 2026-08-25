package com.coachrun.dto.response;

import com.coachrun.entity.Athlete;

import java.time.Instant;
import java.util.UUID;

/**
 * Invitation athlète en attente (vue admin).
 *
 * <p>{@code email}, {@code expired} et {@code clubId} sont <b>ajoutés</b> : la liste ne disait ni
 * à qui l'invitation était partie, ni si elle était déjà périmée — soit précisément les deux
 * informations qui décident entre « relancer » et « laisser courir ».</p>
 */
public record InvitationAdminResponse(
        UUID athleteId,
        String firstName,
        String lastName,
        String clubName,
        Instant expiresAt,
        String email,
        boolean expired,
        UUID clubId) {

    public static InvitationAdminResponse from(Athlete a) {
        Instant expiresAt = a.getInviteExpiresAt();
        return new InvitationAdminResponse(
                a.getId(), a.getFirstName(), a.getLastName(),
                a.getClub().getName(), expiresAt,
                a.getEmail(),
                expiresAt != null && expiresAt.isBefore(Instant.now()),
                a.getClub().getId());
    }
}
