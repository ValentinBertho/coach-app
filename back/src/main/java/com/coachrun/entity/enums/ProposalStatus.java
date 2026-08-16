package com.coachrun.entity.enums;

/** Cycle de vie d'une proposition : en attente d'une décision humaine, acceptée, ou écartée. */
public enum ProposalStatus {

    /** Personne n'a encore tranché. C'est le seul état où la proposition s'affiche. */
    PENDING,

    /** Un humain a accepté : l'écriture a été faite, et {@code decidedBy} dit par qui. */
    ACCEPTED,

    /** Un humain a refusé. Conservée : refuser trois fois la même suggestion est une information. */
    DISMISSED,

    /**
     * La proposition n'a plus de sens (la séance a été supprimée, la date est passée, la valeur
     * a été saisie à la main entre-temps). Périmée par le système, jamais par un humain — et
     * distincte d'un refus, qu'elle ne doit pas polluer.
     */
    EXPIRED
}
