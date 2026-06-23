package com.coachrun.entity.enums;

/**
 * Niveau de permission d'un coach <em>non référent</em> sur un athlète (cf. DARI Lab).
 * L'ordre déclaratif est significatif : {@code READ < COMMENT < WRITE}.
 * <ul>
 *   <li>{@code READ} : dashboard, calendrier, profil physio, analyses — aucune modification.</li>
 *   <li>{@code COMMENT} : lecture + messagerie + retours de séance.</li>
 *   <li>{@code WRITE} : accès complet — prescrire, modifier, remplacer le coach référent.</li>
 * </ul>
 */
public enum PermissionLevel {
    READ,
    COMMENT,
    WRITE;

    /** {@code true} si ce niveau confère au moins les capacités de {@code other}. */
    public boolean atLeast(PermissionLevel other) {
        return other != null && this.ordinal() >= other.ordinal();
    }

    /** Retourne le niveau le plus élevé des deux (l'un ou l'autre pouvant être {@code null}). */
    public static PermissionLevel max(PermissionLevel a, PermissionLevel b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
