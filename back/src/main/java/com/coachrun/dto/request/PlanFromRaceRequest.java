package com.coachrun.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

/**
 * Paramètres du plan construit depuis une course.
 *
 * <p>Tous facultatifs : le geste normal est « construis-moi le plan jusqu'à cette course », sans
 * réglage. Les curseurs existent pour le coach qui veut trancher lui-même, pas pour lui imposer
 * quatre décisions avant de voir quoi que ce soit.</p>
 *
 * @param sourceWeekStart semaine de référence (n'importe quel jour, ramené au lundi) — la semaine
 *                        type de l'athlète, que le plan décline. Défaut : la semaine courante.
 * @param firstWeekStart  première semaine du plan. Défaut : la semaine prochaine.
 * @param peakFactor      volume au sommet, en multiple de la semaine de référence (1,3 = +30 %).
 * @param taperWeeks      semaines d'affûtage avant la course.
 * @param deloadEvery     une semaine de décharge toutes N semaines (0 = aucune).
 * @param overwrite       vrai pour remplir aussi les semaines qui portent déjà des séances. Faux
 *                        par défaut : un plan ne détruit pas le travail existant sans le dire.
 */
public record PlanFromRaceRequest(
        LocalDate sourceWeekStart,
        LocalDate firstWeekStart,
        @Min(1) @Max(3) Double peakFactor,
        @Min(0) @Max(4) Integer taperWeeks,
        @Min(0) @Max(8) Integer deloadEvery,
        Boolean overwrite
) {

    public double peakFactorOrDefault() {
        return peakFactor == null ? 1.3 : peakFactor;
    }

    public int taperWeeksOrDefault() {
        return taperWeeks == null ? com.coachrun.engine.PlanBuilderEngine.DEFAULT_TAPER_WEEKS : taperWeeks;
    }

    public int deloadEveryOrDefault() {
        return deloadEvery == null ? com.coachrun.engine.PlanBuilderEngine.DEFAULT_DELOAD_EVERY : deloadEvery;
    }
}
