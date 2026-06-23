package com.coachrun.dto.response;

import com.coachrun.entity.Athlete1rmProfile;

import java.math.BigDecimal;
import java.util.UUID;

/** 1RM courant d'un athlète pour un exercice. */
public record Athlete1rmResponse(
        UUID exerciseId,
        BigDecimal rmKg,
        String source
) {

    public static Athlete1rmResponse from(Athlete1rmProfile p) {
        return new Athlete1rmResponse(p.getExerciseId(), p.getRmKg(), p.getSource());
    }
}
