package com.coachrun.entity.enums;

/**
 * Où en est un signalement.
 *
 * <p>Trois états, et la distinction entre les deux derniers compte plus qu'il n'y paraît :
 * {@link #ACTED_UPON} et {@link #DISMISSED} racontent des histoires opposées sur la <b>fiche</b>,
 * pas sur le signalant. Les fondre en un seul « traité » ferait perdre la seule statistique qui
 * dise si le dispositif sert à quelque chose — la part des signalements qui aboutissent.</p>
 */
public enum CoachReportStatus {

    /** Reçu, personne ne l'a encore regardé. */
    OPEN,

    /** Examiné, et la fiche a été corrigée ou suspendue. */
    ACTED_UPON,

    /** Examiné, et il n'y avait rien à corriger. */
    DISMISSED;

    /** Vrai tant que le signalement attend un arbitrage. */
    public boolean isOpen() {
        return this == OPEN;
    }

    public String label() {
        return switch (this) {
            case OPEN -> "À traiter";
            case ACTED_UPON -> "Suite donnée";
            case DISMISSED -> "Sans suite";
        };
    }
}
