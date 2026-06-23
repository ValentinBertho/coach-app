package com.coachrun.dto.request;

import com.coachrun.entity.enums.RmFormula;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Calcul du e1RM (cf. DARI Lab). {@code rir} ou {@code rpe} (RIR = 10 − RPE) ; formule NUZZO
 * par défaut.
 */
public record E1rmRequest(
        @NotNull @DecimalMin("0.5") Double weight,
        @NotNull @Min(1) @Max(30) Integer reps,
        @Min(0) @Max(10) Integer rir,
        @DecimalMin("1.0") Double rpe,
        RmFormula formula
) {
}
