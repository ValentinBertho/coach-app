package com.coachrun.dto.response;

import java.util.UUID;

/**
 * Alerte actionnable du tableau de bord coach : un athlète qui nécessite une attention
 * (douleur, charge à risque, séances manquées, silence). Triées par gravité côté service.
 *
 * @param severity {@code RED} (critique) ou {@code ORANGE} (à surveiller)
 * @param type     code stable de la règle déclenchée (PAIN, ACWR_HIGH, ACWR_LOW, MONOTONY, MISSED, SILENCE)
 */
public record CoachAlertResponse(
        UUID athleteId,
        String athleteName,
        String discipline,
        String severity,
        String type,
        String title,
        String detail) {
}
