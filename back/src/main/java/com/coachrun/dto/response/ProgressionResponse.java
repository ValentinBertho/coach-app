package com.coachrun.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Progression auto + alertes coach d'une séance de force réalisée (cf. DARI Lab §6.7 / §6.8).
 */
public record ProgressionResponse(
        UUID scheduledId,
        List<ExerciseProgression> exercises,
        List<AlertResponse> alerts
) {

    /** Suggestion de progression pour un exercice. */
    public record ExerciseProgression(
            UUID exerciseId,
            String exerciseName,
            boolean recommended,
            String suggestionLabel,
            Double deltaKg
    ) {
    }

    /** Alerte coach rattachée à un exercice. */
    public record AlertResponse(
            String level,
            String code,
            String message,
            UUID exerciseId,
            String exerciseName
    ) {
    }
}
