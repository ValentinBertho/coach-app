package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

/**
 * Check-in matinal : trois curseurs, moins de dix secondes.
 * Tous les champs sont facultatifs — un check-in partiel vaut mieux que pas de check-in.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WellnessCheckInRequest(
        /** Jour concerné ; par défaut aujourd'hui côté serveur. */
        LocalDate checkDate,
        @Min(1) @Max(10) Integer sleep,
        @Min(1) @Max(10) Integer fatigue,
        @Min(0) @Max(10) Integer pain
) {
}
