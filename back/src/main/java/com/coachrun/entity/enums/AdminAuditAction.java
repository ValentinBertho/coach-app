package com.coachrun.entity.enums;

/**
 * Geste d'administration consigné au journal.
 *
 * <p>L'énumération est volontairement <b>fermée et explicite</b> plutôt qu'une chaîne libre : un
 * journal ne se relit que s'il se filtre, et un libellé composé à l'appel finit toujours par
 * exister en trois orthographes. Ajouter une valeur est additif — jamais en retirer, des lignes
 * en base la portent (cf. {@code AdminAuditLog#action}, lu en tolérant l'inconnu).</p>
 */
public enum AdminAuditAction {

    // --- Comptes ---
    USER_CREATED("Compte créé"),
    USER_UPDATED("Compte modifié"),
    USER_ROLE_CHANGED("Rôle modifié"),
    USER_SUSPENDED("Compte suspendu"),
    USER_REACTIVATED("Compte réactivé"),
    USER_DELETED("Compte supprimé"),
    USER_PASSWORD_RESET_SENT("Lien de réinitialisation envoyé"),
    USER_VERIFICATION_RESENT("E-mail de vérification renvoyé"),
    USER_SESSIONS_REVOKED("Sessions fermées"),
    USER_CLUB_ADDED("Club rattaché"),
    USER_CLUB_REMOVED("Club détaché"),
    USER_IMPERSONATED("Session ouverte au nom d'un utilisateur"),

    // --- Clubs ---
    CLUB_CREATED("Club créé"),
    CLUB_UPDATED("Club modifié"),
    CLUB_STATUS_CHANGED("Statut du club modifié"),
    CLUB_DELETED("Club supprimé"),

    // --- Athlètes & invitations ---
    ATHLETE_UPDATED("Athlète modifié"),
    ATHLETE_DELETED("Athlète supprimé"),
    INVITATION_REVOKED("Invitation révoquée"),
    INVITATION_RESENT("Invitation renvoyée"),

    // --- Plateforme ---
    STRAVA_WEBHOOK_CREATED("Abonnement Strava créé"),
    STRAVA_WEBHOOK_DELETED("Abonnement Strava retiré"),
    DEMO_RESET("Réinitialisation du jeu de démonstration");

    private final String label;

    AdminAuditAction(String label) {
        this.label = label;
    }

    /** Libellé français, affiché tel quel par le back-office. */
    public String label() {
        return label;
    }

    /** Vrai pour les gestes irréversibles ou à fort pouvoir : mis en évidence dans le journal. */
    public boolean sensitive() {
        return switch (this) {
            case USER_DELETED, CLUB_DELETED, ATHLETE_DELETED, USER_ROLE_CHANGED,
                 USER_IMPERSONATED, USER_SUSPENDED, DEMO_RESET -> true;
            default -> false;
        };
    }
}
