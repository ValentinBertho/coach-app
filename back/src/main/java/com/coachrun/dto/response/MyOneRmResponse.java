package com.coachrun.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** 1RM courant d'un athlète enrichi du nom d'exercice — vue portail /me (CDC §10). */
public record MyOneRmResponse(
        UUID exerciseId,
        String exerciseName,
        BigDecimal rmKg,
        String source
) {
}
