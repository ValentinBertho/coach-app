package com.coachrun.entity.enums;

/**
 * Où en est la fiche publique d'un coach.
 *
 * <p>Décalque volontaire de {@link ClubRequestStatus} : la plateforme arbitre déjà les demandes de
 * création de club à la main, et une place de marché se juge à son pire profil. Reprendre le même
 * cycle, c'est prolonger une pratique établie plutôt qu'en inventer une seconde.</p>
 */
public enum CoachProfileStatus {

    /** En cours d'écriture, visible du seul coach. C'est l'état d'une fiche qui vient de naître. */
    DRAFT,

    /** Soumise, en attente d'arbitrage. Le coach ne peut plus la modifier sans la ressortir. */
    PENDING,

    /** Publiée : elle apparaît dans l'annuaire et accepte les demandes de coaching. */
    PUBLISHED,

    /**
     * Retirée par la plateforme (signalement, manquement). Distincte de {@link #CLOSED}, qui est
     * une décision du coach : confondre les deux reviendrait à faire porter au coach une sanction,
     * ou à laisser croire à une sanction là où il n'y a qu'une pause.
     */
    SUSPENDED,

    /**
     * Le coach ne prend plus d'athlètes. La fiche reste consultable — un athlète qui la cherche
     * doit pouvoir constater qu'elle existe — mais elle n'accepte plus de demande.
     */
    CLOSED;

    /** Vrai si la fiche est visible du public. */
    public boolean isVisible() {
        return this == PUBLISHED || this == CLOSED;
    }

    /** Vrai si la fiche accepte une demande de coaching. */
    public boolean acceptsRequests() {
        return this == PUBLISHED;
    }

    /** Libellé français, affiché tel quel. */
    public String label() {
        return switch (this) {
            case DRAFT -> "Brouillon";
            case PENDING -> "En validation";
            case PUBLISHED -> "Publiée";
            case SUSPENDED -> "Suspendue";
            case CLOSED -> "Ne prend plus d'athlètes";
        };
    }
}
