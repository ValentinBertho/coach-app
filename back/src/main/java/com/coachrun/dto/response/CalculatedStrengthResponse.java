package com.coachrun.dto.response;

import com.coachrun.dto.strength.StrengthBlock;
import com.coachrun.dto.strength.StrengthExerciseItem;

import java.util.List;

/**
 * Séance de force calculée pour un athlète : chaque exercice avec sa charge cible (kg) dérivée
 * de son 1RM courant.
 */
public record CalculatedStrengthResponse(List<CalculatedStrengthBlock> blocks) {

    public record CalculatedStrengthBlock(StrengthBlock block, List<CalculatedExercise> exercises) {
    }

    public record CalculatedExercise(StrengthExerciseItem item, ChargeTarget charge) {
    }
}
