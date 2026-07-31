package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Check-in matinal : trois curseurs, moins de dix secondes.
 *
 * <p>Tous les champs sont facultatifs — un athlète qui ne bouge qu'un curseur envoie quand même
 * son signal. Seuls {@code fatigue} et {@code pain} alimentent l'état de forme ; {@code sleep}
 * est un contexte.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DailyCheckInRequest(
        @Min(0) @Max(10) Integer sleep,
        @Min(0) @Max(10) Integer fatigue,
        @Min(0) @Max(10) Integer pain) {
}
