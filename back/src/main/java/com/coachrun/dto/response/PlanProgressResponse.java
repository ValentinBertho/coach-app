package com.coachrun.dto.response;

import java.time.LocalDate;

/**
 * Avancement d'un plan pour un athlète : date de départ, durée, semaine courante et part de
 * séances réalisées (calculées à partir des séances rattachées au plan).
 */
public record PlanProgressResponse(
        LocalDate startDate,
        int durationWeeks,
        int currentWeek,
        long totalSessions,
        long completedSessions,
        int percent,
        boolean finished) {
}
