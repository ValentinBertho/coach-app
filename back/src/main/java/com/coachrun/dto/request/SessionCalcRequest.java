package com.coachrun.dto.request;

import com.coachrun.entity.enums.PrescriptionRef;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Demande de calcul des cibles d'un bloc de séance pour un athlète (prescription en fourchette).
 */
public record SessionCalcRequest(
        @NotNull PrescriptionRef ref,
        @NotNull @DecimalMin("30.0") @DecimalMax("150.0") Double minPct,
        @NotNull @DecimalMin("30.0") @DecimalMax("150.0") Double maxPct,
        Integer reps,
        Integer distanceM,
        Integer durationS
) {

    @AssertTrue(message = "minPct doit être inférieur ou égal à maxPct.")
    public boolean isRangeValid() {
        return minPct == null || maxPct == null || minPct <= maxPct;
    }
}
