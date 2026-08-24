package com.coachrun.entity.enums;

/**
 * Rôle d'un coach au sein d'un club (cf. DARI Lab — multi-coach / club).
 * <ul>
 *   <li>{@code OWNER} : tout pouvoir (inviter/retirer des coachs, écriture sur tous les athlètes
 *       club, facturation).</li>
 *   <li>{@code COACH_PRINCIPAL} : écriture par défaut sur tous les athlètes <b>club</b>, et peut
 *       conserver des athlètes privés — qui restent hors de portée des autres coachs, lui
 *       compris.</li>
 *   <li>{@code COACH_ASSISTANT} : accès limité aux athlètes assignés ou permissions accordées.</li>
 * </ul>
 */
public enum ClubRole {
    OWNER,
    COACH_PRINCIPAL,
    COACH_ASSISTANT
}
