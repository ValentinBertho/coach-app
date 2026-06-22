package com.coachrun.entity.enums;

/**
 * Rôle d'un coach au sein d'un club (cf. DARI Lab — multi-coach / club).
 * <ul>
 *   <li>{@code OWNER} : tout pouvoir (inviter/retirer des coachs, voir tous les athlètes club, facturation).</li>
 *   <li>{@code COACH_PRINCIPAL} : voit tous les athlètes club (lecture par défaut), gère ses athlètes en écriture,
 *       peut conserver des athlètes privés.</li>
 *   <li>{@code COACH_ASSISTANT} : accès limité aux athlètes assignés ou permissions accordées.</li>
 * </ul>
 */
public enum ClubRole {
    OWNER,
    COACH_PRINCIPAL,
    COACH_ASSISTANT
}
