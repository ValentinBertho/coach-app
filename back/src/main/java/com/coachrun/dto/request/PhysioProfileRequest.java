package com.coachrun.dto.request;

import com.coachrun.entity.enums.Discipline;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * Mise à jour du profil physiologique d'un athlète (cf. DARI Lab). Tous les champs sont
 * optionnels : un {@code null} efface la valeur (ou laisse la valeur par défaut pour les bornes).
 * {@code fcMax} est mappé sur le {@code hrMax} de l'athlète.
 */
public record PhysioProfileRequest(
        Discipline discipline,

        @DecimalMin("0.5") @DecimalMax("12.0") BigDecimal lt1Ms,
        @DecimalMin("0.5") @DecimalMax("12.0") BigDecimal lt2Ms,
        @DecimalMin("0.5") @DecimalMax("12.0") BigDecimal vcMs,

        @Min(60) @Max(260) Integer fcMax,
        @Min(60) @Max(260) Integer fcLt1,
        @Min(60) @Max(260) Integer fcLt2,

        @DecimalMin("50.0") @DecimalMax("120.0") BigDecimal vcDomain1Pct,
        @DecimalMin("50.0") @DecimalMax("120.0") BigDecimal vcDomain2Pct,
        @DecimalMin("50.0") @DecimalMax("110.0") BigDecimal fcDomain1Pct,
        @DecimalMin("50.0") @DecimalMax("110.0") BigDecimal fcDomain2Pct
) {
}
