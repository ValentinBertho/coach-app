package com.coachrun.dto.response;

/**
 * Résultat d'un test de Vitesse Critique. {@code avgHr} est la FC moyenne des efforts pondérée par
 * leur durée (null si aucune FC n'a été relevée) : c'est la FC tenue autour de la VC.
 */
public record VcTestResponse(double vcMs, double vcKmh, double dPrimeM, Integer avgHr) {}
