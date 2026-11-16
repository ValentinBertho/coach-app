package com.coachrun.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Un lien d'invitation, vu par le coach qui l'a créé. */
public record InviteLinkResponse(
        UUID id,
        String token,
        String url,
        String label,
        boolean clubMembership,
        boolean directEntry,
        UUID groupId,
        String groupName,
        Integer maxUses,
        int uses,
        Instant expiresAt,
        Instant revokedAt,
        /** Pourquoi le lien ne sert plus, ou null s'il est actif. */
        String inactiveReason,
        Instant createdAt) {
}
