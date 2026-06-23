package com.coachrun.entity.enums;

/**
 * État de forme d'un athlète (cf. DARI Lab). Calculé exclusivement à partir de la
 * <strong>fatigue + douleur</strong> (jamais du RPE de séance).
 */
public enum FormStatus {
    GREEN,
    ORANGE,
    RED
}
