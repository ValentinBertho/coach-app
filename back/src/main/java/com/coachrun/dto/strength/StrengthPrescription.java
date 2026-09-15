package com.coachrun.dto.strength;

import com.coachrun.entity.enums.ChargeRefType;
import com.coachrun.entity.enums.EffortRefType;
import com.coachrun.entity.enums.SideMode;
import com.coachrun.entity.enums.VolumeType;

/**
 * Prescription d'un exercice de force (cf. DARI Lab) : un référentiel de charge ET un référentiel
 * d'effort indépendants, plus volume, tempo, repos et douleur max tolérée (réathlétisation).
 *
 * <p>Le volume se compte en <strong>répétitions, en durée ou en distance</strong>
 * ({@code volumeType}) — une planche ne se prescrit pas en reps — et chaque unité admet une
 * valeur exacte ou une fourchette (min/max). {@code sideMode} dit si le volume vaut pour les
 * deux côtés, s'alterne, ou vaut par côté.</p>
 *
 * <p>Champs historiques conservés : une prescription enregistrée avant l'ajout de
 * {@code volumeType} n'a pas ce champ en base ({@code null}) et se lit alors en répétitions —
 * ou en durée pour un bloc d'isométrie, seul endroit où {@code durationSec} servait.</p>
 */
public record StrengthPrescription(
        ChargeRefType chargeRefType,
        Double chargeKgMin, Double chargeKgMax,
        Double chargePctRmMin, Double chargePctRmMax,

        EffortRefType effortRefType,
        Double rpeMin, Double rpeMax,
        Integer rirMin, Integer rirMax,

        Integer sets,
        VolumeType volumeType,
        Integer repsFixed, Integer repsMin, Integer repsMax,
        Integer durationSec, Integer durationSecMax,
        Integer distanceM, Integer distanceMMax,
        Integer plyoContacts,
        SideMode sideMode,

        String tempo,
        Integer restSecMin, Integer restSecMax,
        Integer maxPainAllowed
) {

    /**
     * Même prescription, charges remplacées. Les services qui décalent ou mettent à l'échelle une
     * charge ne touchent qu'à ces quatre bornes ; passer par ce constructeur évite de recopier
     * toute la prescription position par position — recopie qui perdait silencieusement un champ
     * à chaque ajout.
     */
    public StrengthPrescription withCharges(Double kgMin, Double kgMax, Double pctMin, Double pctMax) {
        return new StrengthPrescription(
                chargeRefType, kgMin, kgMax, pctMin, pctMax,
                effortRefType, rpeMin, rpeMax, rirMin, rirMax,
                sets, volumeType, repsFixed, repsMin, repsMax,
                durationSec, durationSecMax, distanceM, distanceMMax, plyoContacts, sideMode,
                tempo, restSecMin, restSecMax, maxPainAllowed);
    }

    /** Unité de volume effective : celle prescrite, ou les répétitions par défaut (historique). */
    public VolumeType effectiveVolumeType() {
        return volumeType == null ? VolumeType.REPS : volumeType;
    }
}
