package com.coachrun.entity.enums;

/**
 * Nature d'une blessure déclarée par l'athlète au débrief.
 *
 * <p>Volontairement court : une liste de vingt entrées se parcourt mal au pouce, le soir, après
 * une séance. Ce sont les huit natures qu'un coureur nomme spontanément ; tout le reste passe par
 * {@link #OTHER} et sa note libre — mieux vaut un « autre » explicite qu'un choix approximatif
 * dans une taxonomie médicale que l'athlète n'a pas.</p>
 */
public enum InjuryKind {
    /** Entorse. */
    SPRAIN,
    /** Déchirure / claquage musculaire. */
    STRAIN,
    /** Fracture (y compris de fatigue). */
    FRACTURE,
    /** Tendinite. */
    TENDINITIS,
    /** Contracture. */
    CONTRACTURE,
    /** Courbature. */
    SORENESS,
    /** Ampoule / frottement. */
    BLISTER,
    /** Autre : la note libre porte alors la description. */
    OTHER
}
