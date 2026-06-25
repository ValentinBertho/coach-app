package com.coachrun.dto.response;

/** Résultat d'un test de Vitesse Critique. */
public record VcTestResponse(double vcMs, double vcKmh, double dPrimeM) {}
