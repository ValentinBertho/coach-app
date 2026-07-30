package com.coachrun.engine;

import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.dto.response.CalculatedSessionResponse.CalculatedBlockEntry;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Charge <strong>prévue</strong> d'une séance course, en unités arbitraires (UA).
 *
 * <p>Même méthode que la charge réalisée (sRPE de Foster : RPE × durée en minutes), mais
 * appliquée à la prescription : RPE de bloc × durée estimée du bloc, récupérations comprises.
 * Permet d'afficher un total hebdomadaire en charge à côté des kilomètres — le volume seul ne
 * dit rien de la difficulté d'une semaine.</p>
 *
 * <p>Un bloc sans RPE prescrit ne contribue pas : mieux vaut une charge sous-estimée qu'une
 * valeur inventée. Logique pure, testable sans contexte Spring.</p>
 */
@Component
public class PlannedLoadEngine {

    /** Charge prévue en UA, ou {@code null} si aucun bloc n'est exploitable. */
    public Integer compute(CalculatedSessionResponse calc) {
        if (calc == null) {
            return null;
        }
        double load = 0;
        load += sectionLoad(calc.warmup());
        load += sectionLoad(calc.main());
        load += sectionLoad(calc.cooldown());
        return load > 0 ? (int) Math.round(load) : null;
    }

    private double sectionLoad(List<CalculatedBlockEntry> entries) {
        if (entries == null) {
            return 0;
        }
        double total = 0;
        for (CalculatedBlockEntry e : entries) {
            if (e.block() == null || e.block().rpe() == null) {
                continue;
            }
            int rpe = e.block().rpe();
            // La durée estimée du bloc inclut déjà ses répétitions (calcul amont).
            Integer effortS = e.calc() == null ? null : e.calc().estimatedDurationS();
            if (effortS != null && effortS > 0) {
                total += rpe * (effortS / 60.0);
            }
            // Les récupérations ne portent pas de RPE prescrit : plaquer celui du bloc dessus
            // gonflerait artificiellement la charge d'un fractionné. Elles sont donc exclues,
            // ce qui rend la charge prévue légèrement conservatrice — c'est le bon sens d'erreur.
        }
        return total;
    }
}
