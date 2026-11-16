package com.coachrun.dto.response;

/**
 * Ce que montre la page d'un lien d'invitation, avant toute connexion : qui invite, où, et ce qui
 * se passera en rejoignant.
 */
public record InvitePreviewResponse(
        boolean valid,
        /** Pourquoi le lien ne sert plus ; null s'il est valable. */
        String reason,
        String coachName,
        /** Fiche publique du coach, si elle est publiée. */
        String coachSlug,
        String coachPhotoUrl,
        String clubName,
        boolean independentCoach,
        boolean clubMembership,
        String groupName,
        boolean directEntry) {
}
