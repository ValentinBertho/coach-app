package com.coachrun.dto.response;

import java.util.List;

/** Indicateurs du tableau de bord coach. */
public record CoachDashboardResponse(
        long activeAthletes,
        long pendingInvitations,
        long sessionsToReview,
        long completedThisWeek,
        List<RaceObjectiveResponse> upcomingRaces) {
}
