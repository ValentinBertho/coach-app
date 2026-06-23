package com.coachrun.engine;

import com.coachrun.entity.enums.IntensityDomain;
import org.springframework.stereotype.Component;

/**
 * Classification d'un effort dans un domaine d'intensité (cf. DARI Lab — annexe).
 * Priorité à la physiologie (seuils LT1/LT2 en vitesse) ; à défaut, repli sur la FC en % de FCmax ;
 * sinon domaine 1 par défaut (conservatif).
 */
@Component
public class IntensityDomainEngine {

    /**
     * @param speedMs       vitesse de l'effort en m/s (peut être {@code null})
     * @param hrBpm         fréquence cardiaque de l'effort (peut être {@code null})
     * @param lt1Ms         seuil LT1 en m/s (peut être {@code null})
     * @param lt2Ms         seuil LT2 en m/s (peut être {@code null})
     * @param fcMax         FC maximale (peut être {@code null})
     * @param fcDomain1Pct  borne haute du domaine 1 en % de FCmax
     * @param fcDomain2Pct  borne haute du domaine 2 en % de FCmax
     */
    public IntensityDomain classify(Double speedMs, Integer hrBpm,
                                    Double lt1Ms, Double lt2Ms,
                                    Integer fcMax, double fcDomain1Pct, double fcDomain2Pct) {
        // 1. Physiologie (LT1/LT2) prioritaire.
        if (speedMs != null && lt1Ms != null && lt2Ms != null) {
            if (speedMs < lt1Ms) {
                return IntensityDomain.DOMAIN_1;
            }
            if (speedMs <= lt2Ms) {
                return IntensityDomain.DOMAIN_2;
            }
            return IntensityDomain.DOMAIN_3;
        }

        // 2. Repli FC en % de FCmax.
        if (fcMax != null && fcMax > 0 && hrBpm != null) {
            double hrPct = (hrBpm * 100.0) / fcMax;
            if (hrPct < fcDomain1Pct) {
                return IntensityDomain.DOMAIN_1;
            }
            if (hrPct <= fcDomain2Pct) {
                return IntensityDomain.DOMAIN_2;
            }
            return IntensityDomain.DOMAIN_3;
        }

        // 3. Défaut conservatif.
        return IntensityDomain.DOMAIN_1;
    }
}
