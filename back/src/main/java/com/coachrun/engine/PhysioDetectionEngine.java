package com.coachrun.engine;

import com.coachrun.entity.enums.RunDistance;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * « Ton niveau a bougé » : ce qu'une sortie dit du profil de l'athlète.
 *
 * <h2>Le manque qu'il comble</h2>
 *
 * <p>Le VDOT ne bougeait que si un humain saisissait un chrono. Pendant ce temps, les activités
 * importées contenaient tout ce qu'il fallait — tours, flux, FC max observée. Un athlète qui court
 * un 10 km en compétition et synchronise sa montre gardait ses allures d'il y a trois mois, alors
 * que la cascade de recalcul (VDOT → allures → zones → cibles) n'attendait qu'un chiffre.</p>
 *
 * <h2>La règle qui ne se négocie pas</h2>
 *
 * <p>Ce moteur <b>propose</b>. Il ne met jamais un profil à jour : une valeur détectée dans une
 * sortie n'a pas le statut d'une valeur mesurée en test, et le coach est seul à pouvoir décider
 * qu'un effort était une performance de référence plutôt qu'une sortie en descente avec le vent
 * dans le dos. C'est la même règle que pour le 1RM, où le testé prime sur l'estimé.</p>
 *
 * <h2>Ce qu'il refuse de proposer</h2>
 *
 * <ul>
 *   <li>un effort trop court pour être significatif (moins de 3 km) ;</li>
 *   <li>un gain dérisoire (moins d'un point de VDOT) — le coach n'a pas à trancher du bruit ;</li>
 *   <li>un gain invraisemblable (plus de 6 points d'un coup), qui trahit une mesure fausse
 *       bien plus souvent qu'un progrès ;</li>
 *   <li>une descente : un profil ne se dégrade pas parce qu'on a couru lentement un dimanche.</li>
 * </ul>
 *
 * <p>Logique pure, testable sans contexte Spring.</p>
 */
@Component
public class PhysioDetectionEngine {

    /** Un effort continu observé dans une activité. */
    public record Effort(int distanceM, int durationS) {
    }

    /** Ce que le moteur propose de retenir. */
    public record Detection(
            RunDistance distance,
            int distanceM,
            int timeSeconds,
            double vdot,
            double previousVdot,
            double gain,
            String title,
            String reason) {
    }

    /** En deçà, un effort ne dit rien de fiable du niveau. */
    private static final int MIN_DISTANCE_M = 3000;
    /** Gain minimal pour valoir une décision humaine. */
    private static final double MIN_GAIN = 1.0;
    /** Gain au-delà duquel on soupçonne la mesure plutôt que l'athlète. */
    private static final double MAX_PLAUSIBLE_GAIN = 6.0;

    private final VdotEngine vdotEngine;

    public PhysioDetectionEngine(VdotEngine vdotEngine) {
        this.vdotEngine = vdotEngine;
    }

    /**
     * Meilleure détection d'une activité, s'il y en a une.
     *
     * @param efforts      efforts continus candidats (l'activité entière, et ses tours)
     * @param currentVdot  VDOT actuel, ou {@code null} si l'athlète n'en a pas encore
     */
    public Detection detect(List<Effort> efforts, Double currentVdot) {
        if (efforts == null || efforts.isEmpty()) {
            return null;
        }
        Detection best = null;
        for (Effort e : efforts) {
            if (e.distanceM() < MIN_DISTANCE_M || e.durationS() <= 0) {
                continue;
            }
            double vdot = vdotEngine.vdot(e.distanceM(), e.durationS());
            if (currentVdot != null) {
                double gain = vdot - currentVdot;
                if (gain < MIN_GAIN || gain > MAX_PLAUSIBLE_GAIN) {
                    continue;
                }
            }
            if (best == null || vdot > best.vdot()) {
                best = build(e, vdot, currentVdot);
            }
        }
        return best;
    }

    private Detection build(Effort e, double vdot, Double currentVdot) {
        RunDistance distance = nearest(e.distanceM());
        double previous = currentVdot == null ? 0 : currentVdot;
        double gain = currentVdot == null ? 0 : round(vdot - currentVdot);

        String label = distance != null ? distance.code() : (round(e.distanceM() / 1000.0) + " km");
        String title = currentVdot == null
                ? "Premier repère de niveau : " + label + " en " + format(e.durationS()) + "."
                : "Ta sortie vaut mieux que ton profil : VDOT " + round(vdot)
                        + " (+" + gain + ").";
        String reason = label + " en " + format(e.durationS())
                + (currentVdot == null ? "." : " — VDOT actuel " + round(currentVdot) + ".");

        return new Detection(distance, e.distanceM(), e.durationS(),
                round(vdot), round(previous), gain, title, reason);
    }

    /**
     * Distance de référence la plus proche, à 5 % près.
     *
     * <p>À défaut, {@code null} : enregistrer 11,4 km comme un « 10 km » fausserait le record de
     * l'athlète, et la performance de référence est aussi ce qui s'affiche dans ses records.</p>
     */
    private RunDistance nearest(int distanceM) {
        RunDistance best = null;
        double bestError = 0.05;
        for (RunDistance d : RunDistance.values()) {
            if (!d.hasFixedDistance()) {
                continue;
            }
            double error = Math.abs(d.meters() - distanceM) / (double) d.meters();
            if (error <= bestError) {
                bestError = error;
                best = d;
            }
        }
        return best;
    }

    private String format(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
