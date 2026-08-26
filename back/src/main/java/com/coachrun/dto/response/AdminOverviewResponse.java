package com.coachrun.dto.response;

import java.util.List;

/**
 * Tableau de bord d'administration : ce qu'un administrateur doit voir en arrivant.
 *
 * <p>Ordre délibéré : d'abord ce qui ne va pas ({@code signals}), ensuite la photographie
 * ({@code counts}), puis la dynamique ({@code growth}, {@code engagement}), enfin les canaux et le
 * journal. On lit de haut en bas et on s'arrête dès qu'il n'y a plus rien à décider.</p>
 *
 * <p>Remplace {@code AdminStatsResponse}, qui reste servi tel quel sur {@code /admin/stats} :
 * des clients PWA en cache l'appellent encore (cf. Claude.md §4 bis — on ajoute, on ne retire pas).</p>
 */
public record AdminOverviewResponse(
        List<AdminSignalResponse> signals,
        Counts counts,
        Growth growth,
        Engagement engagement,
        List<AdminIntegrationResponse> integrations,
        List<AdminAuditResponse> recentActions) {

    /** Photographie de la plateforme. */
    public record Counts(
            long clubs,
            long clubsActive,
            long clubsSuspended,
            long users,
            long admins,
            long headCoaches,
            long coaches,
            long athleteAccounts,
            long usersSuspended,
            long usersUnverified,
            long athletes,
            long athletesActive,
            long athletesPaused,
            long athletesArchived,
            long pendingInvitations,
            long workouts,
            long activities) {
    }

    /** Ce qui est arrivé récemment : la seule lecture qui distingue croissance et stagnation. */
    public record Growth(
            long newUsers7d,
            long newUsers30d,
            long newClubs30d,
            long newAthletes30d) {
    }

    /**
     * Usage réel. {@code activeUsers*} s'appuie sur {@code users.last_seen_at}, entretenu à un
     * quart d'heure près : un compte « actif sur 24 h » a réellement ouvert l'application.
     */
    public record Engagement(
            long activeUsers24h,
            long activeUsers7d,
            long activeUsers30d,
            long activities7d,
            long workoutsPlanned7d,
            long workoutsCompleted7d,
            long adminActions7d) {
    }
}
