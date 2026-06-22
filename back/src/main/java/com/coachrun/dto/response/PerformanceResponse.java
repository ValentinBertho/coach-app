package com.coachrun.dto.response;

import com.coachrun.entity.AthletePerformance;
import com.coachrun.entity.enums.RunDistance;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Performance de référence d'un athlète, avec le VDOT qu'elle implique (si distance fixe).
 */
public record PerformanceResponse(
        UUID id,
        RunDistance distance,
        String distanceCode,
        int timeSeconds,
        LocalDate dateSet,
        Double vdot
) {

    public static PerformanceResponse from(AthletePerformance p, Double vdot) {
        return new PerformanceResponse(
                p.getId(), p.getDistance(), p.getDistance().code(),
                p.getTimeSeconds(), p.getDateSet(), vdot);
    }
}
