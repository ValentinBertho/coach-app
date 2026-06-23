package com.coachrun.dto.response;

import com.coachrun.entity.EstimatedOneRm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Point d'historique du e1RM (courbe d'évolution de la force). */
public record E1rmHistoryResponse(
        UUID exerciseId,
        BigDecimal e1rmKg,
        BigDecimal chargeKg,
        int reps,
        String rpeOrRir,
        Instant calculatedAt
) {

    public static E1rmHistoryResponse from(EstimatedOneRm e) {
        return new E1rmHistoryResponse(
                e.getExerciseId(), e.getE1rmKg(), e.getChargeKg(), e.getReps(),
                e.getRpeOrRir(), e.getCreatedAt());
    }
}
