package com.coachrun.entity.enums;

/**
 * Sens de « difficulté » d'une métrique : indique si une valeur plus haute correspond à un
 * effort plus dur (FC, puissance) ou plus facile (allure = plus le chrono monte, plus c'est lent).
 * Utilisé pour ordonner min/max et présenter les fourchettes de façon cohérente.
 */
public enum MetricDirection {
    /** Une valeur plus élevée = plus dur (FC, vitesse, puissance, %FCmax, RPE). */
    HIGHER_HARDER,
    /** Une valeur plus élevée = plus facile (allure en s/km : un chrono plus grand est plus lent). */
    LOWER_HARDER
}
