package com.coachrun.dto.response;

import com.coachrun.dto.session.CourseBlock;

import java.util.List;

/**
 * Séance course entièrement calculée pour un athlète : chaque bloc avec ses cibles (allure/FC/RPE…),
 * la récupération calculée le cas échéant, et les totaux estimés de la séance.
 */
public record CalculatedSessionResponse(
        List<CalculatedBlockEntry> warmup,
        List<CalculatedBlockEntry> main,
        List<CalculatedBlockEntry> cooldown,
        Integer totalDistanceM,
        Integer totalDurationS
) {

    /** Un bloc avec ses cibles calculées (et celles de sa récupération si prescrite). */
    public record CalculatedBlockEntry(
            CourseBlock block,
            CalculatedBlockResponse calc,
            CalculatedBlockResponse recoveryCalc
    ) {
    }
}
