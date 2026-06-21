package com.coachrun.dto.response;

import java.time.Instant;

/** Lien d'invitation magique généré pour un athlète. */
public record AthleteInvitationResponse(String inviteUrl, Instant expiresAt) {
}
