package com.coachrun.entity.enums;

/**
 * Regroupement des notifications tel que l'utilisateur le règle.
 *
 * <p>La préférence porte sur la <b>catégorie</b>, pas sur le type technique. Un écran qui
 * proposerait onze interrupteurs — un par code émis — demanderait à l'utilisateur de connaître le
 * découpage interne du produit pour arbitrer ; quatre familles se choisissent en un coup d'œil, et
 * un nouveau type rejoint sa famille sans rien changer à l'écran ni aux préférences déjà posées.</p>
 *
 * <p>Couper une catégorie ne coupe que le <b>push</b> : le centre de notifications reste
 * intégralement alimenté, sans quoi l'utilisateur perdrait l'information et pas seulement
 * l'interruption.</p>
 */
public enum NotificationCategory {

    /**
     * Ce qui bouge sur l'entraînement de l'athlète : séance déposée, plan attribué, séance
     * déplacée ou annulée, activité remontée de la montre, record battu.
     */
    PROGRAMME,

    /** Ce qui relance au bon moment : rappel de séance, débriefing, course qui approche. */
    RAPPELS,

    /** Ce qu'une personne a écrit : message du fil, commentaire de séance. */
    MESSAGES,

    /** Ce que le coach doit surveiller : retours, alertes, indisponibilités, consentement. */
    SUIVI;

    /**
     * Catégorie d'un type de notification. Un type inconnu — un code ajouté sans être classé —
     * tombe dans {@link #SUIVI} plutôt que d'échapper aux préférences : mieux vaut qu'il soit
     * coupable avec le reste que muet aux réglages.
     */
    public static NotificationCategory of(String type) {
        if (type == null) {
            return SUIVI;
        }
        return switch (type) {
            case "WORKOUT_PLANNED", "PLAN_ASSIGNED", "WORKOUT_UPDATED", "STRENGTH_PLANNED",
                 "ACTIVITY_IMPORTED", "PERSONAL_RECORD" -> PROGRAMME;
            // Le bilan hebdomadaire est le seul message qui ne demande rien : il raconte. Il se
            // règle donc avec les rappels — le rendez-vous périodique — et non avec le suivi.
            case "WORKOUT_REMINDER", "SESSION_DEBRIEF", "RACE_REMINDER", "WEEKLY_RECAP" -> RAPPELS;
            // Le « vu » du coach est un mot adressé à l'athlète, pas un signal de suivi : il se
            // règle avec les messages, sans quoi couper les alertes de suivi couperait aussi la
            // seule reconnaissance que l'athlète reçoit de son coach.
            case "NEW_MESSAGE", "COACH_COMMENT", "COACH_ACK" -> MESSAGES;
            default -> SUIVI;
        };
    }
}
