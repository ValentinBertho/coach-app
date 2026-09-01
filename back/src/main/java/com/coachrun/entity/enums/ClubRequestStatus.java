package com.coachrun.entity.enums;

/** Où en est une demande de création de club, côté back-office plateforme. */
public enum ClubRequestStatus {

    /** Déposée, pas encore arbitrée. C'est la file du matin. */
    PENDING,

    /** Validée : le club et le compte du coach ont été créés à ce moment-là. */
    APPROVED,

    /** Refusée. La demande reste en base — un refus se relit, et se conteste. */
    REJECTED;

    /** Libellé français, affiché tel quel par le back-office. */
    public String label() {
        return switch (this) {
            case PENDING -> "En attente";
            case APPROVED -> "Validée";
            case REJECTED -> "Refusée";
        };
    }
}
