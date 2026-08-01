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

    /**
     * Allure d'entraînement (secondes/km) tenue à un pourcentage donné du VDOT — méthode Daniels :
     * la vitesse est celle dont la demande en O₂ vaut {@code pctVdot × VDOT}.
     *
     * <p>Sert aux deux allures que le coach lit en premier : l'endurance fondamentale (≈ 70 %) et
     * le seuil (≈ 88 %), qu'aucune équivalence de course ne donnait jusqu'ici.</p>
     */
    public int trainingPaceSecPerKm(double targetVdot, double pctVdot) {
        double velocityMetersPerMin = velocityForVo2(targetVdot * pctVdot);
        return (int) Math.round(60_000.0 / velocityMetersPerMin);
    }

    /** Allure d'endurance fondamentale (« easy »). */
    public int easyPaceSecPerKm(double targetVdot) {
        return trainingPaceSecPerKm(targetVdot, EASY_PCT);
    }

    /** Allure au seuil (« threshold ») : 88 % du VDOT — l'allure tenable ~60 min. */
    public int thresholdPaceSecPerKm(double targetVdot) {
        return trainingPaceSecPerKm(targetVdot, THRESHOLD_PCT);
    }

    /**
     * Part du VDOT tenue en endurance fondamentale. Calée sur la table de Daniels plutôt que sur
     * le milieu théorique de la plage E (59–74 %) : à 70 % on sortait de la fourchette publiée
     * (5:06/km pour un VDOT 50, contre 5:09–5:41 en table), soit une « easy » trop rapide.
     */
    public static final double EASY_PCT = 0.68;
    /** Part du VDOT tenue au seuil lactique (Daniels) — retombe sur la table (4:15/km à VDOT 50). */
    public static final double THRESHOLD_PCT = 0.88;

    /**
     * Vitesse (m/min) dont le coût énergétique vaut {@code vo2}, par inversion de la relation
     * vitesse → VO₂ de Daniels (polynôme du second degré, strictement croissant sur le domaine utile).
     */
    private double velocityForVo2(double vo2) {
        double lo = 1.0;
        double hi = 1_000.0; // 1000 m/min = 60 km/h : bien au-delà de tout coureur
        for (int i = 0; i < 100; i++) {
            double mid = (lo + hi) / 2.0;
            if (vo2AtVelocity(mid) < vo2) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2.0;
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
        double vo2 = vo2AtVelocity(velocityMetersPerMin);
        double minutes = timeSeconds / 60.0;
        double percentMax = 0.8
                + 0.1894393 * Math.exp(-0.012778 * minutes)
                + 0.2989558 * Math.exp(-0.1932605 * minutes);
        return vo2 / percentMax;
    }

    /** Demande en O₂ (ml/kg/min) d'une vitesse de course (m/min) — relation de Daniels. */
    private double vo2AtVelocity(double velocityMetersPerMin) {
        return -4.60 + 0.182258 * velocityMetersPerMin
                + 0.000104 * velocityMetersPerMin * velocityMetersPerMin;
    }
}
