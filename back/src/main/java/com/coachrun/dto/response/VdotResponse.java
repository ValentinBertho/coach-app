package com.coachrun.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * VDOT courant d'un athlète et ses allures d'équivalence de course par distance.
 */
public record VdotResponse(
        BigDecimal vdot,
        List<VdotPaceItem> paces
) {

    /** Allure d'équivalence pour une distance : secondes/km, label « m:ss » et vitesse km/h. */
    public record VdotPaceItem(
            String distance,
            Integer paceSecPerKm,
            String paceLabel,
            Double speedKmh
    ) {
    }
}
