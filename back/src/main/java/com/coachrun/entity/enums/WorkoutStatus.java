package com.coachrun.entity.enums;

import java.util.Set;

/** Machine à états d'une séance : PLANNED → COMPLETED / PARTIAL / MISSED. */
public enum WorkoutStatus {
    PLANNED,
    COMPLETED,
    PARTIAL,
    MISSED;

    /**
     * Libellé français, destiné aux messages lus par un humain (notifications, e-mails).
     *
     * <p>Sans lui, la concaténation d'un statut dans un message affiche le nom de la constante :
     * le coach recevait « Séance mise à jour — Marie COMPLETED ».</p>
     */
    public String label() {
        return switch (this) {
            case PLANNED -> "planifiée";
            case COMPLETED -> "réalisée";
            case PARTIAL -> "partielle";
            case MISSED -> "non faite";
        };
    }

    /** Transitions autorisées depuis l'état courant (validées en service). */
    public boolean canTransitionTo(WorkoutStatus target) {
        return switch (this) {
            case PLANNED -> Set.of(COMPLETED, PARTIAL, MISSED).contains(target);
            case COMPLETED, PARTIAL, MISSED -> target == PLANNED || this == target;
        };
    }
}
