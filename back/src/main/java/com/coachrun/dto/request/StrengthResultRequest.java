package com.coachrun.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Une série réalisée d'un exercice de force (retour athlète). */
public record StrengthResultRequest(
        @NotNull UUID exerciseId,
        int setNumber,
        BigDecimal chargeKg,
        Integer repsDone,
        Integer durationSecDone,
        BigDecimal rpeDone,
        Integer rirDone,
        Integer pain,
        String comment
) {
}
