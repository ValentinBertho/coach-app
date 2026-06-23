package com.coachrun.dto.response;

import com.coachrun.entity.ScheduledStrengthSession;

import java.time.LocalDate;
import java.util.UUID;

/** Séance de force planifiée (résumé pour le calendrier). */
public record ScheduledStrengthResponse(
        UUID id,
        UUID athleteId,
        UUID sourceSessionId,
        String title,
        LocalDate scheduledDate,
        LocalDate originalDate,
        boolean movedByAthlete,
        boolean completed,
        Integer sessionFatigue,
        Integer sessionPain
) {

    public static ScheduledStrengthResponse from(ScheduledStrengthSession s) {
        return new ScheduledStrengthResponse(
                s.getId(), s.getAthlete().getId(), s.getSourceSessionId(), s.getTitle(),
                s.getScheduledDate(), s.getOriginalDate(), s.isMovedByAthlete(), s.isCompleted(),
                s.getSessionFatigue(), s.getSessionPain());
    }
}
