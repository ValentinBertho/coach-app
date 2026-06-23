package com.coachrun.dto.response;

import com.coachrun.entity.LactateTestStep;

import java.math.BigDecimal;

/** Palier d'un test lactate (valeurs déchiffrées pour le tracé de la courbe). */
public record LactateTestStepResponse(
        int stepOrder,
        BigDecimal speedMs,
        Double speedKmh,
        Integer hr,
        BigDecimal lactate,
        Integer rpe,
        Integer durationS
) {

    public static LactateTestStepResponse from(LactateTestStep s) {
        Double kmh = s.getSpeedMs() == null ? null
                : Math.round(s.getSpeedMs().doubleValue() * 3.6 * 10.0) / 10.0;
        return new LactateTestStepResponse(
                s.getStepOrder(), s.getSpeedMs(), kmh, s.getHr(),
                s.getLactate(), s.getRpe(), s.getDurationS());
    }
}
