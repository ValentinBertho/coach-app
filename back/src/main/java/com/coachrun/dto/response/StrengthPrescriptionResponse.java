package com.coachrun.dto.response;

import com.coachrun.dto.strength.StrengthStructure;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Prescription figée d'une séance de force planifiée : snapshot des blocs, charges calculées et
 * champs demandés à l'athlète.
 */
public record StrengthPrescriptionResponse(
        StrengthStructure snapshot,
        CalculatedStrengthResponse calculated,
        JsonNode requiredFields
) {
}
