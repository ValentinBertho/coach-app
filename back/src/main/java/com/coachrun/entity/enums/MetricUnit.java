package com.coachrun.entity.enums;

/**
 * Unité physique d'une métrique de zone (cf. AUDIT-COACH-ZONES-METRIQUES §3.1).
 * L'unité pilote la mise en forme côté front et l'interprétation des valeurs saisies/synchronisées.
 */
public enum MetricUnit {
    /** Allure : secondes par kilomètre. */
    S_PER_KM,
    /** Fréquence cardiaque : battements par minute. */
    BPM,
    /** Vitesse : kilomètres par heure. */
    KMH,
    /** Pourcentage (ex. %FCmax). */
    PCT,
    /** Puissance : watts. */
    W,
    /** Effort perçu (échelle RPE). */
    RPE
}
