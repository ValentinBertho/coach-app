package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Création d'un lien d'invitation par un coach. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InviteLinkRequest(
        @Size(max = 120) String label,
        /** Rejoindre le club, et pas seulement le coach. Ignoré pour un coach indépendant. */
        boolean clubMembership,
        /** Entrée directe, sans validation (lien personnel). */
        boolean directEntry,
        UUID groupId,
        @Min(1) @Max(1000) Integer maxUses,
        /** Validité en jours ; vide = sans fin. */
        @Min(1) @Max(365) Integer validDays) {
}
