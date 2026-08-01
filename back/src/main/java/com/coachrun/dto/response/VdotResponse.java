package com.coachrun.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * VDOT courant d'un athlète, ses allures d'équivalence de course par distance et ses allures
 * d'entraînement (endurance fondamentale, seuil) dérivées du même VDOT.
 */
public record VdotResponse(
        BigDecimal vdot,
        List<VdotPaceItem> paces,
        /** Allures d'entraînement (« easy », « seuil ») — vide tant qu'aucun VDOT n'est connu. */
        List<VdotPaceItem> trainingPaces
) {

    /** Sans VDOT : ni équivalences ni allures d'entraînement. */
    public static VdotResponse empty() {
        return new VdotResponse(null, List.of(), List.of());
    }

    /** Allure d'équivalence pour une distance : secondes/km, label « m:ss » et vitesse km/h. */
    public record VdotPaceItem(
            String distance,
            Integer paceSecPerKm,
            String paceLabel,
            Double speedKmh
    ) {
    }
}
