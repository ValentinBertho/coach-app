package com.coachrun.entity.enums;

/**
 * Nature d'une {@link com.coachrun.entity.AthleteProposal} — ce que la proposition ferait si
 * quelqu'un l'acceptait.
 *
 * <p>Chaque valeur correspond à une branche du dispatcher
 * ({@code ProposalService#apply}) et à une forme de charge utile
 * ({@code AthleteProposal.payloadJson}). Ajouter un type sans ajouter sa branche est une erreur
 * que le dispatcher signale bruyamment plutôt que d'ignorer en silence : une proposition qu'on
 * accepte et qui ne fait rien est pire que pas de proposition du tout.</p>
 */
public enum ProposalType {

    /**
     * Alléger la séance du jour (volume réduit, intensité conservée) — issue du feu vert du matin
     * ou demandée par l'athlète. Cible : une séance course.
     */
    SESSION_LIGHTEN,

    /**
     * Retirer l'intensité en gardant le volume (une douleur ne se traite pas en coupant le
     * kilométrage, elle se traite en coupant les allures rapides). Cible : une séance course.
     */
    SESSION_EASY,

    /** Décaler une séance à une autre date. Cible : une séance course. */
    SESSION_MOVE,

    /** Supprimer une séance devenue sans objet (période d'indisponibilité). Cible : une séance. */
    SESSION_DROP,

    /**
     * Mettre à jour une valeur physiologique détectée dans une activité (VDOT, vitesse critique,
     * FC max). Cible : l'athlète. Ne remplace jamais une valeur <b>mesurée</b> en test sans que le
     * coach l'ait acceptée — c'est tout l'objet de la validation.
     */
    PHYSIO_UPDATE,

    /**
     * Ajuster la charge d'un exercice de renforcement à la prochaine séance (surcharge
     * progressive). Cible : l'athlète + l'exercice, porté dans la charge utile.
     */
    STRENGTH_LOAD
}
