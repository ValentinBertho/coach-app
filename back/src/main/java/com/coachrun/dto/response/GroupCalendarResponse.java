package com.coachrun.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Semaine d'un groupe d'entraînement : une ligne par athlète, séances course et force incluses.
 *
 * <p>Endpoint agrégé pour éviter N appels depuis le calendrier coach en mode groupe. Les
 * athlètes sur lesquels le coach n'a aucun droit de lecture sont absents de la réponse (jamais
 * masqués côté client) ; {@code canWrite} porte le droit de prescription athlète par athlète.</p>
 */
public record GroupCalendarResponse(
        UUID groupId,
        String groupName,
        List<AthleteRow> athletes) {

    public record AthleteRow(
            UUID athleteId,
            String firstName,
            String lastName,
            boolean canWrite,
            List<WorkoutResponse> workouts,
            List<ScheduledStrengthResponse> strength) {
    }
}
