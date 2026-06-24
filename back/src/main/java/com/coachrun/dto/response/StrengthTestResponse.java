package com.coachrun.dto.response;

import com.coachrun.entity.StrengthTest;
import com.coachrun.entity.enums.StrengthTestProtocol;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Test de force enregistré, avec son e1RM dérivé. */
public record StrengthTestResponse(
        UUID id,
        UUID exerciseId,
        StrengthTestProtocol protocol,
        LocalDate testDate,
        BigDecimal weightKg,
        Integer reps,
        Integer durationSec,
        Integer rir,
        BigDecimal computedE1rmKg,
        String notes
) {

    public static StrengthTestResponse from(StrengthTest t) {
        return new StrengthTestResponse(
                t.getId(), t.getExerciseId(), t.getProtocol(), t.getTestDate(),
                t.getWeightKg(), t.getReps(), t.getDurationSec(), t.getRir(),
                t.getComputedE1rmKg(), t.getNotes());
    }
}
