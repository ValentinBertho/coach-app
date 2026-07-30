package com.coachrun.dto.request;

import com.coachrun.entity.enums.PrescriptionRef;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.util.UUID;

/**
 * Demande de calcul des cibles d'un bloc pour un athlète. Deux modes :
 * <ul>
 *   <li><b>Zone (Z3)</b> : {@code zoneId} — la cible est lue sur la fiche athlète ;</li>
 *   <li><b>Legacy</b> : {@code ref + minPct/maxPct} — fourchette en % d'un référentiel (aperçu d'anciens contenus).</li>
 * </ul>
 */
public record SessionCalcRequest(
        UUID zoneId,
        /** Zone cardio facultative accompagnant {@code zoneId} : d'où vient la cible FC. */
        UUID hrZoneId,
        PrescriptionRef ref,
        @DecimalMin("30.0") @DecimalMax("150.0") Double minPct,
        @DecimalMin("30.0") @DecimalMax("150.0") Double maxPct,
        Integer reps,
        Integer distanceM,
        Integer durationS
) {

    @AssertTrue(message = "Fournir soit une zone, soit un référentiel avec sa fourchette (ref + minPct + maxPct).")
    public boolean isPrescriptionValid() {
        if (zoneId != null) {
            return true;
        }
        return ref != null && minPct != null && maxPct != null && minPct <= maxPct;
    }
}
