package com.coachrun.dto.response;

import com.coachrun.engine.PaceUtil;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.enums.Discipline;

import java.math.BigDecimal;

/**
 * Profil physiologique d'un athlète (cf. DARI Lab). Les seuils sont exposés en m/s et,
 * pour confort d'affichage, convertis en km/h.
 */
public record PhysioProfileResponse(
        Discipline discipline,
        BigDecimal lt1Ms, BigDecimal lt2Ms, BigDecimal vcMs,
        Double lt1Kmh, Double lt2Kmh, Double vcKmh,
        Integer fcMax, Integer fcLt1, Integer fcLt2,
        BigDecimal vcDomain1Pct, BigDecimal vcDomain2Pct,
        BigDecimal fcDomain1Pct, BigDecimal fcDomain2Pct,
        BigDecimal vdot
) {

    public static PhysioProfileResponse from(Athlete a) {
        return new PhysioProfileResponse(
                a.getDiscipline(),
                a.getLt1Ms(), a.getLt2Ms(), a.getVcMs(),
                kmh(a.getLt1Ms()), kmh(a.getLt2Ms()), kmh(a.getVcMs()),
                a.getHrMax(), a.getFcLt1(), a.getFcLt2(),
                a.getVcDomain1Pct(), a.getVcDomain2Pct(),
                a.getFcDomain1Pct(), a.getFcDomain2Pct(),
                a.getVdot());
    }

    private static Double kmh(BigDecimal ms) {
        if (ms == null) {
            return null;
        }
        return Math.round(ms.doubleValue() * 3.6 * 10.0) / 10.0;
    }
}
