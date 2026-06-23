package com.coachrun.dto.strength;

import com.coachrun.entity.enums.ChargeRefType;
import com.coachrun.entity.enums.EffortRefType;

/**
 * Prescription d'un exercice de force (cf. DARI Lab) : un référentiel de charge ET un référentiel
 * d'effort indépendants, plus volume, tempo, repos et douleur max tolérée (réathlétisation).
 */
public record StrengthPrescription(
        ChargeRefType chargeRefType,
        Double chargeKgMin, Double chargeKgMax,
        Double chargePctRmMin, Double chargePctRmMax,

        EffortRefType effortRefType,
        Double rpeMin, Double rpeMax,
        Integer rirMin, Integer rirMax,

        Integer sets,
        Integer repsFixed, Integer repsMin, Integer repsMax,
        Integer durationSec,
        Integer plyoContacts,

        String tempo,
        Integer restSecMin, Integer restSecMax,
        Integer maxPainAllowed
) {
}
