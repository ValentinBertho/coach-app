package com.coachrun.dto.response;

import com.coachrun.entity.enums.AthleteOwnershipType;

import java.util.List;
import java.util.UUID;

/** Accès d'un athlète : statut privé/club, coach référent et permissions accordées. */
public record AthleteAccessResponse(
        AthleteOwnershipType ownership,
        UUID referentCoachId,
        String referentName,
        List<PermissionEntry> permissions
) {
    public record PermissionEntry(UUID coachId, String name, String permission, String expiresAt) {
    }
}
