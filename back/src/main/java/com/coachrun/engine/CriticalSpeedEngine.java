package com.coachrun.engine;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Vitesse Critique (modèle 2 paramètres) : à partir de plusieurs efforts maximaux
 * (distance d_i parcourue en temps t_i), régression linéaire d = CS·t + D'.
 * La pente CS est la vitesse critique (m/s), l'ordonnée D' la réserve anaérobie (m).
 */
@Component
public class CriticalSpeedEngine {

    public record Trial(double distanceM, double timeS) {}
    public record Result(double vcMs, double dPrimeM) {}

    public Result compute(List<Trial> trials) {
        if (trials == null || trials.size() < 2) {
            throw new IllegalArgumentException("Au moins deux efforts sont nécessaires.");
        }
        int n = trials.size();
        double sumT = 0, sumD = 0, sumTT = 0, sumTD = 0;
        for (Trial tr : trials) {
            if (tr.timeS() <= 0 || tr.distanceM() <= 0) {
                throw new IllegalArgumentException("Distance et temps doivent être positifs.");
            }
            sumT += tr.timeS();
            sumD += tr.distanceM();
            sumTT += tr.timeS() * tr.timeS();
            sumTD += tr.timeS() * tr.distanceM();
        }
        double denom = n * sumTT - sumT * sumT;
        if (denom == 0) {
            throw new IllegalArgumentException("Les temps des efforts doivent être distincts.");
        }
        double cs = (n * sumTD - sumT * sumD) / denom;        // pente = vitesse critique (m/s)
        double dPrime = (sumD - cs * sumT) / n;               // ordonnée = D' (m)
        if (cs <= 0) {
            throw new IllegalArgumentException("Vitesse critique non calculable (efforts incohérents).");
        }
        return new Result(cs, dPrime);
    }
}
