package com.coachrun.entity.enums;

/**
 * Motif d'une séance déclarée non faite par l'athlète.
 *
 * <p>Volontairement court : l'objectif est qu'un athlète clôture sa séance en un tap depuis son
 * téléphone, pas qu'il remplisse un formulaire. Le commentaire libre reste disponible à côté pour
 * tout ce qui ne rentre pas dans ces cinq cases.</p>
 *
 * <p>{@link #HEALTH} est la seule valeur qui révèle un état de santé (art. 9 RGPD) : elle est
 * ramenée à {@link #OTHER} quand le consentement de l'athlète n'est pas actif, exactement comme le
 * motif d'une indisponibilité. Une blessure durable a de toute façon sa place dans les
 * indisponibilités, pas dans un motif de séance manquée.</p>
 */
public enum MissedReason {
    /** Imprévu : déplacement, obligation familiale, journée qui déborde. */
    UNEXPECTED,
    /** Pas eu le temps — le créneau n'a pas pu être tenu. */
    NO_TIME,
    /** Météo ou conditions (verglas, canicule, terrain impraticable). */
    WEATHER,
    /** Santé : fatigue inhabituelle, douleur, maladie. Donnée de l'article 9. */
    HEALTH,
    /** Autre motif, ou motif non précisé. */
    OTHER
}
