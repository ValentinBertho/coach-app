package com.coachrun.dto.response;

import com.coachrun.engine.LactateThresholdEngine.LTDetectionResult;

/** Seuils détectés (m/s + km/h pour confort d'affichage). */
public record LTDetectionResponse(
        Double baseline,
        Double lt1Threshold,
        Double lt1Ms, Double lt2Ms,
        Double lt1Kmh, Double lt2Kmh,
        Integer fcLt1, Integer fcLt2
) {

    public static LTDetectionResponse from(LTDetectionResult r) {
        return new LTDetectionResponse(
                r.baseline(), r.lt1Threshold(), r.lt1Ms(), r.lt2Ms(),
                kmh(r.lt1Ms()), kmh(r.lt2Ms()), r.fcLt1(), r.fcLt2());
    }

    private static Double kmh(Double ms) {
        return ms == null ? null : Math.round(ms * 3.6 * 10.0) / 10.0;
    }
}
