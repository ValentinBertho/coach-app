package com.coachrun.entity.enums;

/**
 * Unité de volume d'un exercice de force : ce que compte la série.
 *
 * <p>Un exercice ne se prescrit pas toujours en répétitions — une planche se tient en secondes,
 * un fermier se porte sur une distance. Sans ce choix, tout se traduisait en « reps », et une
 * planche de 45 s se prescrivait « 45 répétitions ».</p>
 */
public enum VolumeType {
    /** Répétitions (valeur exacte ou fourchette). */
    REPS,
    /** Durée en secondes (gainage, isométrie, portés). */
    DUREE,
    /** Distance en mètres (fermier, poussée de traîneau, marche lestée). */
    DISTANCE
}
