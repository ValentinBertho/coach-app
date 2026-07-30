package com.coachrun.entity.enums;

/**
 * Valeur de référence (« ancre ») à partir de laquelle une bande de zone est calculée.
 * Cf. PROPOSITION-ZONES-ET-EDITEUR-V2 §3.2. Les ancres d'allure se résolvent en allure de base via
 * le moteur (seuil mesuré ou repli VDOT) ; les ancres de FC se calculent directement.
 */
public enum ZoneAnchor {
    // --- Allure / vitesse (résolues en allure de base) ---
    LT1, LT2, VC,
    /** Vitesse maximale aérobie (km/h). */
    VMA,
    PACE_800M, PACE_1500M, PACE_3000M, PACE_5KM, PACE_10KM, PACE_15KM, PACE_SEMI, PACE_MARATHON,
    // --- Fréquence cardiaque ---
    /** % de la FC maximale. */
    FCMAX,
    /** % de la FC au seuil lactique (LT2). */
    LTHR,
    /** % de la réserve cardiaque (Karvonen : FC_repos + %·(FCmax − FC_repos)). */
    HRR
}
