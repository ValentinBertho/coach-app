package com.coachrun.dto.response;

import com.coachrun.engine.SessionCalculatorEngine;
import com.coachrun.entity.enums.PrescriptionRef;

/**
 * Cibles calculées d'un bloc de séance pour un athlète : allure, vitesse, FC, RPE, durée, distance.
 */
public record CalculatedBlockResponse(
        boolean computable,
        PrescriptionRef ref,
        Integer basePaceSecPerKm,
        Integer paceMinSecPerKm, Integer paceMaxSecPerKm,
        String paceMinLabel, String paceMaxLabel,
        Double speedMinKmh, Double speedMaxKmh,
        Integer hrMin, Integer hrMax,
        Integer rpeMin, Integer rpeMax,
        Integer estimatedDurationS, Integer estimatedDistanceM,
        /** Allure estimée depuis le VDOT (pas de seuil mesuré) — à signaler dans l'UI. */
        boolean paceEstimated
) {

    public static CalculatedBlockResponse from(SessionCalculatorEngine.Result r) {
        return new CalculatedBlockResponse(
                r.computable(), r.ref(), r.basePaceSecPerKm(),
                r.paceMinSecPerKm(), r.paceMaxSecPerKm(),
                r.paceMinLabel(), r.paceMaxLabel(),
                r.speedMinKmh(), r.speedMaxKmh(),
                r.hrMin(), r.hrMax(),
                r.rpeMin(), r.rpeMax(),
                r.estimatedDurationS(), r.estimatedDistanceM(),
                r.paceEstimated());
    }
}
