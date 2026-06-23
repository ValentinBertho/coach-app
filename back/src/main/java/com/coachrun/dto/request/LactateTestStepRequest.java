package com.coachrun.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Palier d'un test lactate : vitesse (m/s), FC, lactate, RPE, durée. */
public record LactateTestStepRequest(
        @NotNull @DecimalMin("0.5") BigDecimal speedMs,
        Integer hr,
        BigDecimal lactate,
        Integer rpe,
        Integer durationS
) {
}
