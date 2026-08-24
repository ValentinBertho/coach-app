package com.coachrun.entity.enums;

/**
 * Qui voit un groupe d'entraînement.
 *
 * <p>Ne concerne que le groupe et son fil : la visibilité de chaque <b>athlète</b> reste régie par
 * la relation référente et les permissions. Superposer deux mécanismes de confidentialité les
 * rendrait tous deux inexplicables — d'autant qu'un athlète peut appartenir à plusieurs groupes.</p>
 */
public enum GroupVisibility {
    /** Tous les coachs du club. */
    CLUB,
    /** Son créateur et les coachs qu'il y invite. */
    PRIVATE
}
