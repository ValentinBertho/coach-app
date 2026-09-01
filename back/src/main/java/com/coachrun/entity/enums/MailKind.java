package com.coachrun.entity.enums;

/**
 * Nature d'un e-mail sortant, telle qu'on veut la lire dans le suivi de consommation.
 *
 * <p>Le code est <b>stable</b> et sert d'axe d'analyse : il répond à « qu'est-ce qui consomme mon
 * quota ce mois-ci ». Le sujet de l'e-mail, lui, varie avec le contenu (un nom d'athlète, un
 * nombre de séances) et ne se regroupe pas.</p>
 *
 * <p>La distinction qui compte pour l'exploitation : les envois <b>transactionnels</b> ne peuvent
 * pas être perdus — une réinitialisation de mot de passe ou une invitation n'a pas d'autre canal —
 * là où les envois de <b>routine</b> ont toujours un repli (le centre de notifications, le push).
 * Quand un plafond menace, c'est la routine qu'on coupe.</p>
 */
public enum MailKind {

    /** Confirmation d'adresse à l'inscription. Transactionnel. */
    VERIFICATION(true),

    /** Réinitialisation de mot de passe. Transactionnel — et le plus coûteux à perdre. */
    PASSWORD_RESET(true),

    /** Invitation d'un coach dans un club. Transactionnel. */
    COACH_INVITATION(true),

    /** Invitation d'un athlète. Transactionnel. */
    ATHLETE_INVITATION(true),

    /**
     * Suite donnée à une demande de création de club : accusé de réception, validation (qui porte
     * le lien d'activation) ou refus. Transactionnel — la validation est le seul moyen d'entrer.
     */
    CLUB_REQUEST(true),

    /** Digest d'alertes du coach, une fois par jour à 7 h. Routine. */
    ALERT_DIGEST(false),

    /** Rappel de séance du lendemain, quand aucun appareil ne peut être joint. Routine. */
    WORKOUT_REMINDER(false),

    /** Message reçu, quand aucun appareil ne peut être joint. Routine. */
    MESSAGE(false),

    /** Indisponibilité déclarée par un athlète. Routine. */
    UNAVAILABILITY(false),

    /**
     * Bilan hebdomadaire du coach, le lundi matin. Routine — un envoi par coach et par semaine,
     * soit le poste le plus prévisible du plan d'envoi.
     */
    WEEKLY_RECAP(false),

    /** Envoi non classé — ne devrait pas apparaître ; sa présence signale un oubli de classement. */
    OTHER(false);

    private final boolean transactional;

    MailKind(boolean transactional) {
        this.transactional = transactional;
    }

    /** Cet envoi est-il de ceux qui n'ont aucun autre canal de secours ? */
    public boolean isTransactional() {
        return transactional;
    }

    /** Libellé français, pour l'écran d'administration. */
    public String label() {
        return switch (this) {
            case VERIFICATION -> "Vérification d'adresse";
            case PASSWORD_RESET -> "Mot de passe oublié";
            case COACH_INVITATION -> "Invitation coach";
            case ATHLETE_INVITATION -> "Invitation athlète";
            case CLUB_REQUEST -> "Demande de club";
            case ALERT_DIGEST -> "Digest d'alertes";
            case WORKOUT_REMINDER -> "Rappel de séance";
            case MESSAGE -> "Message";
            case UNAVAILABILITY -> "Indisponibilité";
            case WEEKLY_RECAP -> "Bilan hebdomadaire";
            case OTHER -> "Non classé";
        };
    }
}
