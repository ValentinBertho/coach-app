package com.coachrun.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Bornes des domaines d'intensité par défaut du club (% de la vitesse critique / de la FC max). */
public record ClubDefaultsRequest(
        @NotNull @DecimalMin("50.0") @DecimalMax("150.0") BigDecimal vcDomain1Pct,
        @NotNull @DecimalMin("50.0") @DecimalMax("150.0") BigDecimal vcDomain2Pct,
        @NotNull @DecimalMin("50.0") @DecimalMax("100.0") BigDecimal fcDomain1Pct,
        @NotNull @DecimalMin("50.0") @DecimalMax("100.0") BigDecimal fcDomain2Pct) {
}
