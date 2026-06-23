package com.coachrun.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** Détection temps réel des seuils (sans persistance) à partir de paliers saisis. */
public record LactateDetectRequest(
        BigDecimal lactateRest,
        @Valid @NotNull List<LactateTestStepRequest> steps
) {
}
