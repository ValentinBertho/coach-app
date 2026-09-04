package com.coachrun.entity.enums;

/**
 * Où en est une demande de coaching.
 *
 * <p>Cinq états, et aucun n'est décoratif : chacun décrit une situation qu'un athlète ou un coach
 * peut réellement rencontrer, et que l'écran doit savoir nommer. Retirer {@link #WITHDRAWN} ou
 * {@link #EXPIRED} au profit d'un {@code DECLINED} générique reviendrait à dire à un athlète que
 * le coach l'a refusé alors qu'il n'a jamais répondu.</p>
 */
public enum CoachingRequestStatus {

    /** Envoyée, en attente d'une réponse du coach. */
    PENDING,

    /** Le coach a accepté : la fiche, la relation et le fil de discussion existent désormais. */
    ACCEPTED,

    /** Le coach a refusé, avec son motif s'il en a donné un. */
    DECLINED,

    /** L'athlète a retiré sa demande avant qu'on y réponde. */
    WITHDRAWN,

    /**
     * Personne n'a répondu dans le délai.
     *
     * <p>Distinct d'un refus, et c'est tout l'intérêt : un coach débordé n'est pas un coach qui
     * dit non, et l'athlète doit pouvoir le relancer ou passer à autre chose sans se croire
     * éconduit.</p>
     */
    EXPIRED;

    /** Vrai tant que la demande attend une décision. */
    public boolean isOpen() {
        return this == PENDING;
    }

    public String label() {
        return switch (this) {
            case PENDING -> "En attente";
            case ACCEPTED -> "Acceptée";
            case DECLINED -> "Refusée";
            case WITHDRAWN -> "Retirée";
            case EXPIRED -> "Sans réponse";
        };
    }
}
