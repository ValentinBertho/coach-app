package com.coachrun.dto.strength;

import com.coachrun.entity.enums.BlockFormat;
import com.coachrun.entity.enums.BlockType;

import java.util.List;

/**
 * Bloc d'une séance de force (cf. DARI Lab). Les paramètres {@code durationSec/rounds/workSec/restSec}
 * ne servent que pour les formats EMOM / AMRAP / Circuit.
 */
public record StrengthBlock(
        String id,
        BlockType blockType,
        BlockFormat format,
        Integer durationSec,
        Integer rounds,
        Integer workSec,
        Integer restSec,
        List<StrengthExerciseItem> exercises
) {

    public StrengthBlock {
        exercises = exercises == null ? List.of() : exercises;
    }
}
