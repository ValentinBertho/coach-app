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

    /**
     * Un bloc avec ses cibles calculées (et celles de sa récupération si prescrite).
     *
     * @param steps cibles des étapes, pour un bloc <b>enchaîné</b> (« 8 × (200 m / 400 m) ») ;
     *              liste vide pour un bloc simple, dont tout est déjà dans {@code calc}. Chaque
     *              étape a sa propre allure : une seule cible par bloc ne pourrait pas les dire.
     */
    public record CalculatedBlockEntry(
            CourseBlock block,
            CalculatedBlockResponse calc,
            CalculatedBlockResponse recoveryCalc,
            List<CalculatedStepEntry> steps
    ) {

        /** Bloc simple : pas d'étapes à calculer. */
        public CalculatedBlockEntry(CourseBlock block, CalculatedBlockResponse calc,
                                    CalculatedBlockResponse recoveryCalc) {
            this(block, calc, recoveryCalc, List.of());
        }
    }

    /** Une étape d'un enchaînement avec ses cibles, et celles de la récup qui la suit. */
    public record CalculatedStepEntry(
            com.coachrun.dto.session.CourseStep step,
            CalculatedBlockResponse calc,
            CalculatedBlockResponse recoveryCalc
    ) {
    }
}
