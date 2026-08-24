package com.coachrun.entity.enums;

/**
 * Nature d'un fil de discussion.
 *
 * <ul>
 *   <li>{@code ATHLETE_COACH} — un binôme athlète↔coach. Deux coachs qui suivent le même athlète
 *       ont deux fils distincts et ne se lisent pas.</li>
 *   <li>{@code COACH_COACH} — deux coachs du même club.</li>
 *   <li>{@code GROUP} — le fil d'un groupe d'entraînement : athlètes et coachs y écrivent.</li>
 *   <li>{@code CLUB} — l'annonce du club : les coachs écrivent, tout le club lit.</li>
 * </ul>
 */
public enum ConversationKind {
    ATHLETE_COACH,
    COACH_COACH,
    GROUP,
    CLUB
}
