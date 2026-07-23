package com.coachrun.entity.enums;

/**
 * Modèle nommé de calcul des zones (façon Nolio : « Configuration utilisée »). Un modèle est un
 * préréglage cohérent d'ancres + pourcentages ; CUSTOM = réglage libre par le coach.
 * Cf. PROPOSITION-ZONES-ET-EDITEUR-V2 (décision : modèles nommés).
 */
public enum ZoneModel {
    /** Allures dérivées de la VMA (% de l'allure à VMA). */
    VMA,
    /** Vitesse Critique (référence 3-5 km). */
    VC,
    /** Daniels / VDOT (allures d'équivalence par distance). */
    DANIELS_VDOT,
    /** Seuils lactiques mesurés (LT1/LT2). */
    LACTATE_THRESHOLD,
    /** % de la FC max / réserve. */
    PCT_FCMAX,
    /** Réglage libre (ancre + % saisis par le coach). */
    CUSTOM
}
