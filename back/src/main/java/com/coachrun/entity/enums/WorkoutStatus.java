package com.coachrun.entity.enums;

import java.util.Set;

/** Machine à états d'une séance : PLANNED → COMPLETED / PARTIAL / MISSED. */
public enum WorkoutStatus {
    PLANNED,
    COMPLETED,
    PARTIAL,
    MISSED;

    /** Transitions autorisées depuis l'état courant (validées en service). */
    public boolean canTransitionTo(WorkoutStatus target) {
        return switch (this) {
            case PLANNED -> Set.of(COMPLETED, PARTIAL, MISSED).contains(target);
            case COMPLETED, PARTIAL, MISSED -> target == PLANNED || this == target;
        };
    }
}
