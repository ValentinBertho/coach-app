package com.coachrun.dto.request;

import com.coachrun.entity.enums.WorkoutStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Feedback de l'athlète sur sa séance : statut réalisé + RPE (1–10) + commentaire. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutFeedbackRequest(
        WorkoutStatus status,
        @Min(1) @Max(10) Integer rpe,
        @Size(max = 1024) String comment) {
}
