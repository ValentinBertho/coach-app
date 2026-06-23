package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Retour global de l'athlète sur une séance de force : RPE séance, fatigue, douleur, commentaire. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrengthFeedbackRequest(
        Boolean completed,
        @DecimalMin("1.0") @DecimalMax("10.0") BigDecimal sessionRpe,
        @Min(1) @Max(10) Integer fatigue,
        @Min(0) @Max(10) Integer pain,
        @Size(max = 1024) String comment
) {
}
