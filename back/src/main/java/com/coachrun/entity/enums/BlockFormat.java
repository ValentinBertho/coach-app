package com.coachrun.entity.enums;

/** Format d'un bloc de force (cf. DARI Lab). */
public enum BlockFormat {
    CLASSIQUE,
    EMOM,
    AMRAP,
    FOR_TIME,
    CIRCUIT,
    ISOMETRIE,
    PLIOMETRIE,
    /** Exercices enchaînés sans pause, répétés en tours ; la récup se prend entre les tours. */
    SUPERSET
}
