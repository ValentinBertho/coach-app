package com.coachrun.dto.request;

import com.coachrun.entity.enums.WorkoutStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Feedback de l'athlète sur sa séance : statut réalisé, RPE (1–10), fatigue (1–10),
 * douleur (0–10) et commentaire. Fatigue + douleur alimentent l'état de forme.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutFeedbackRequest(
        WorkoutStatus status,
        @Min(1) @Max(10) Integer rpe,
        @Min(1) @Max(10) Integer fatigue,
        @Min(0) @Max(10) Integer pain,
        @Size(max = 1024) String comment) {
}
