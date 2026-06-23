package com.coachrun.dto.strength;

import com.coachrun.entity.enums.SetType;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Exercice prescrit dans un bloc de force (cf. DARI Lab). {@code setConfig} porte la configuration
 * spécifique au type de série (drop set, cluster, iso…), structure libre.
 */
public record StrengthExerciseItem(
        UUID exerciseId,
        String exerciseName,
        SetType setType,
        StrengthPrescription prescription,
        JsonNode setConfig,
        String coachNotes
) {
}
