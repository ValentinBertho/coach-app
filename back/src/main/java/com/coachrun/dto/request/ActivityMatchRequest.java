package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Rapprochement manuel d'une activité réalisée avec une séance prescrite.
 *
 * @param workoutId séance à rattacher, ou {@code null} pour détacher l'activité
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActivityMatchRequest(UUID workoutId) {
}
