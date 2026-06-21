package com.coachrun.dto.response;

/** Compteurs du tableau de bord d'administration. */
public record AdminStatsResponse(
        long clubs,
        long headCoaches,
        long coaches,
        long athletes,
        long pendingInvitations,
        long workouts,
        long activities) {
}
