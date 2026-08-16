package com.coachrun.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Le chrono réalisé sur une course.
 *
 * @param timeSeconds temps total, en secondes. Borné à 24 h : au-delà, c'est une faute de saisie,
 *                    et une faute de saisie sur une course devient un VDOT faux qui contamine
 *                    toutes les allures de l'athlète.
 */
public record RaceResultRequest(
        @Min(1) @Max(86_400) int timeSeconds
) {
}
