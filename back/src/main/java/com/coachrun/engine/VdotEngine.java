package com.coachrun.engine;

import org.springframework.stereotype.Component;

/**
 * Moteur VDOT (méthode Jack Daniels — « Running Formula »).
 *
 * <p>Le VDOT est un VO2max effectif déduit d'une performance : on calcule la VO2 à la vitesse
 * de course, divisée par le pourcentage de VO2max soutenable sur la durée de l'effort
 * (modèle exponentiel de Daniels). Les allures d'entraînement / équivalences de course se
 * déduisent ensuite par inversion numérique de la même fonction.</p>
 *
 * <p>Implémentation pure (testable sans contexte Spring) ; exposée en {@link Component} pour
 * l'injection dans les services.</p>
 */
@Component
public class VdotEngine {

    /** VDOT déduit d'une performance (distance en mètres, temps en secondes). */
    public double vdot(int distanceMeters, int timeSeconds) {
        return vdotCore(distanceMeters, timeSeconds);
    }

    /**
     * Allure d'équivalence de course (secondes/km) pour un VDOT donné sur une distance donnée,
     * par inversion numérique (le VDOT est strictement décroissant avec le temps).
     */
    public int racePaceSecPerKm(double targetVdot, int distanceMeters) {
        double timeSeconds = timeForVdot(targetVdot, distanceMeters);
        double secPerKm = timeSeconds / (distanceMeters / 1000.0);
        return (int) Math.round(secPerKm);
    }

    /** Temps (secondes) nécessaire pour couvrir {@code distanceMeters} à {@code targetVdot}. */
    public double timeForVdot(double targetVdot, int distanceMeters) {
        double lo = 1.0;         // borne basse : 1 s
        double hi = 100_000.0;   // borne haute : ~27 h
        for (int i = 0; i < 100; i++) {
            double mid = (lo + hi) / 2.0;
            double v = vdotCore(distanceMeters, mid);
            if (v > targetVdot) {
                // À ce temps le VDOT est trop élevé ⇒ il faut ralentir (plus de temps).
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2.0;
    }

    // --- Cœur Daniels ---------------------------------------------------------

    private double vdotCore(double distanceMeters, double timeSeconds) {
        double velocityMetersPerMin = distanceMeters / (timeSeconds / 60.0);
        double vo2 = -4.60 + 0.182258 * velocityMetersPerMin
                + 0.000104 * velocityMetersPerMin * velocityMetersPerMin;
        double minutes = timeSeconds / 60.0;
        double percentMax = 0.8
                + 0.1894393 * Math.exp(-0.012778 * minutes)
                + 0.2989558 * Math.exp(-0.1932605 * minutes);
        return vo2 / percentMax;
    }
}
