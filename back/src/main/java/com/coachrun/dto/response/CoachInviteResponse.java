package com.coachrun.dto.response;

import com.coachrun.entity.enums.ClubRole;

import java.util.UUID;

/**
 * Résultat de l'ajout d'un coach au club : {@code invited=false} si le coach avait déjà un compte
 * (ajouté immédiatement) ; {@code invited=true} si un compte a été créé en attente — {@code inviteUrl}
 * porte alors le lien d'acceptation (utile si l'e-mail n'est pas configuré).
 */
public record CoachInviteResponse(
        UUID coachId,
        String name,
        ClubRole clubRole,
        boolean invited,
        String inviteUrl) {
}
