package com.coachrun.entity.enums;

/**
 * Statut d'appartenance d'un athlète vis-à-vis du club (cf. DARI Lab).
 * <ul>
 *   <li>{@code PRIVATE} : visible uniquement par le coach référent (clientèle personnelle),
 *       invisible des autres coachs du club, même l'Owner.</li>
 *   <li>{@code CLUB} : visible par les coachs du club selon les permissions.</li>
 * </ul>
 * Déduit en pratique de la relation coach↔athlète : {@code club_id IS NULL} ⇒ {@code PRIVATE}.
 */
public enum AthleteOwnershipType {
    PRIVATE,
    CLUB
}
