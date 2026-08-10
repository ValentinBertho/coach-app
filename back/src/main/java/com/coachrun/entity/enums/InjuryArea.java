package com.coachrun.entity.enums;

/**
 * Zone du corps concernée par une blessure déclarée.
 *
 * <p>Découpage de coureur, pas d'anatomiste : ce sont les régions qui changent la conduite du
 * coach (un tendon d'Achille ne se gère pas comme un ischio-jambier), et elles se choisissent en
 * un tap dans une grille. La latéralité est portée à part par {@link InjurySide}.</p>
 */
public enum InjuryArea {
    FOOT,
    ANKLE,
    ACHILLES,
    CALF,
    SHIN,
    KNEE,
    HAMSTRING,
    QUADRICEPS,
    ADDUCTOR,
    HIP,
    GLUTE,
    LOWER_BACK,
    BACK,
    ABDOMEN,
    SHOULDER,
    OTHER
}
