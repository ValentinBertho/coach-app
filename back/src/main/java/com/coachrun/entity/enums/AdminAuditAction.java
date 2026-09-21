package com.coachrun.entity.enums;

/**
 * Geste consigné au journal.
 *
 * <p><b>Le nom dit « admin », le contenu ne s'y limite plus.</b> L'énumération ne portait au
 * départ que les gestes du back-office. Elle porte désormais aussi ce que font les utilisateurs
 * ordinaires, classé par {@link AdminAuditScope}. Le nom (comme celui de la table
 * {@code admin_audit_log}) reste tel quel : le renommer imposerait une migration destructive pour
 * un gain purement cosmétique, ce que §4 bis interdit — et un journal dont on renomme le contenant
 * perd son historique en chemin.</p>
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
    /**
     * Écriture effectuée <b>pendant</b> une session empruntée.
     *
     * <p>L'ouverture de l'impersonation était consignée, la suite ne l'était pas : une séance
     * déplacée ou un message envoyé depuis un compte emprunté laissait une trace en tout point
     * identique à celle de son titulaire. La cible est le compte emprunté, l'emprunteur figure
     * dans les colonnes dédiées, et le résumé porte la route appelée — jamais le corps de la
     * requête, qui transporte du ressenti et des douleurs.</p>
     */
    IMPERSONATED_WRITE("Écriture en session empruntée"),

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

    // --- Demandes de création de club ---
    CLUB_REQUEST_APPROVED("Demande de club validée"),
    CLUB_REQUEST_REJECTED("Demande de club refusée"),

    // --- Plateforme ---
    STRAVA_WEBHOOK_CREATED("Abonnement Strava créé"),
    STRAVA_WEBHOOK_DELETED("Abonnement Strava retiré"),

    // ------------------------------------------------------------------------
    // Ce que fait un utilisateur ordinaire (portée SECURITY / PRIVACY / COACHING)
    //
    // Le journal ne regardait que le back-office. Or un administrateur supprime un club deux fois
    // par an, quand un coach archive un athlète, retire un consentement santé ou exporte un
    // dossier toutes les semaines — et que personne ne se connecte jamais « pour rien ». Les
    // gestes qu'on cherche après coup (« qui s'est connecté depuis cette adresse ? », « qui a
    // effacé ces données ? », « quand ce compte a-t-il changé de mot de passe ? ») n'avaient
    // aucune trace consultable : au mieux une ligne INFO perdue à la rotation des journaux.
    //
    // Le tri est délibéré : ce qui ouvre un accès, ce qui touche aux données personnelles, ce qui
    // est destructeur ou structurant. Tout le reste — une séance déplacée, un message envoyé, un
    // commentaire — reste hors du journal : il se compte en milliers par jour et se lit déjà dans
    // le produit, à sa place.
    // ------------------------------------------------------------------------

    // --- Sécurité des accès ---
    LOGIN_SUCCEEDED("Connexion"),
    LOGIN_FAILED("Échec de connexion"),
    LOGIN_BLOCKED("Connexion bloquée (trop de tentatives)"),
    LOGOUT("Déconnexion"),
    ACCOUNT_REGISTERED("Inscription"),
    INVITATION_ACCEPTED("Invitation acceptée"),
    EMAIL_VERIFIED("Adresse e-mail vérifiée"),
    EMAIL_CHANGED("Adresse e-mail modifiée"),
    PASSWORD_CHANGED("Mot de passe changé"),
    PASSWORD_RESET_REQUESTED("Réinitialisation de mot de passe demandée"),
    PASSWORD_RESET_COMPLETED("Mot de passe réinitialisé"),

    // --- Données personnelles (RGPD) ---
    HEALTH_CONSENT_GRANTED("Consentement santé donné"),
    HEALTH_CONSENT_WITHDRAWN("Consentement santé retiré"),
    PERSONAL_DATA_EXPORTED("Dossier personnel exporté"),
    ATHLETE_DATA_ERASED("Données d'athlète effacées (droit à l'oubli)"),
    DEVICE_CONNECTED("Montre connectée"),
    DEVICE_DISCONNECTED("Montre déconnectée"),

    // --- Coaching ---
    ATHLETE_CREATED("Athlète créé"),
    ATHLETE_ARCHIVED("Athlète archivé"),
    ATHLETE_INVITED("Athlète invité"),
    COACH_INVITED("Coach invité au club"),
    COACH_REMOVED("Coach retiré du club"),
    TRAINING_PLAN_DELETED("Plan d'entraînement supprimé"),

    /**
     * @deprecated La réinitialisation démo a été retirée à l'ouverture de la bêta : elle effaçait
     *     toutes les données de l'instance, et son seul garde-fou était un profil actif. La valeur
     *     reste déclarée parce que des lignes du journal la portent — en retirer une ferait
     *     échouer la relecture de l'historique.
     */
    @Deprecated
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
                 USER_IMPERSONATED, IMPERSONATED_WRITE, USER_SUSPENDED, CLUB_REQUEST_APPROVED,
                 DEMO_RESET -> true;
            // Gestes d'utilisateurs qui méritent le même traitement : ils effacent des données
            // qui ne reviennent pas, ils déplacent le contrôle d'un compte, ou ils font sortir un
            // dossier entier de la plateforme. L'export est le seul des trois qui soit banal chez
            // un utilisateur légitime — il est aussi le geste type d'un compte détourné, et c'est
            // à ce titre qu'il est mis en évidence.
            case PASSWORD_CHANGED, PASSWORD_RESET_COMPLETED, EMAIL_CHANGED, LOGIN_BLOCKED,
                 HEALTH_CONSENT_WITHDRAWN, PERSONAL_DATA_EXPORTED, ATHLETE_DATA_ERASED,
                 COACH_REMOVED, TRAINING_PLAN_DELETED -> true;
            default -> false;
        };
    }

    /**
     * Famille à laquelle l'action appartient. Voir {@link AdminAuditScope} pour ce que la notion
     * sert à préserver — notamment le bandeau « dernières actions » et le compteur « actions
     * d'administration », que l'ouverture du journal aurait noyés.
     *
     * <p>Le {@code default} rattache à l'administration : une action ajoutée sans être classée
     * ici rejoint le journal historique plutôt que de disparaître d'un écran. Se tromper de
     * famille se corrige ; ne pas apparaître ne se remarque pas.</p>
     */
    public AdminAuditScope scope() {
        return switch (this) {
            case LOGIN_SUCCEEDED, LOGIN_FAILED, LOGIN_BLOCKED, LOGOUT, ACCOUNT_REGISTERED,
                 INVITATION_ACCEPTED, EMAIL_VERIFIED, EMAIL_CHANGED, PASSWORD_CHANGED,
                 PASSWORD_RESET_REQUESTED, PASSWORD_RESET_COMPLETED -> AdminAuditScope.SECURITY;
            case HEALTH_CONSENT_GRANTED, HEALTH_CONSENT_WITHDRAWN, PERSONAL_DATA_EXPORTED,
                 ATHLETE_DATA_ERASED, DEVICE_CONNECTED, DEVICE_DISCONNECTED -> AdminAuditScope.PRIVACY;
            case ATHLETE_CREATED, ATHLETE_ARCHIVED, ATHLETE_INVITED, COACH_INVITED, COACH_REMOVED,
                 TRAINING_PLAN_DELETED -> AdminAuditScope.COACHING;
            default -> AdminAuditScope.ADMINISTRATION;
        };
    }

    /**
     * Les actions d'une famille, ou toutes si aucune n'est demandée.
     *
     * <p><b>Pourquoi une liste et pas un {@code null}.</b> Le filtre de portée se traduit en base
     * par un {@code action in (…)}. Passer {@code null} pour « pas de filtre » obligerait à écrire
     * {@code :actions is null}, c'est-à-dire un paramètre seul dans un {@code is null} — la forme
     * exacte qui a déjà coûté un écran entier en production sur PostgreSQL (cf.
     * {@code AdminAuditLogRepository}, « could not determine data type of parameter »). Une liste
     * toujours pleine dit la même chose sans piège : l'énumération compte quelques dizaines de
     * valeurs, ce qui ne pèse rien dans un {@code in}.</p>
     */
    public static java.util.List<AdminAuditAction> inScope(AdminAuditScope scope) {
        return java.util.Arrays.stream(values())
                .filter(a -> scope == null || a.scope() == scope)
                .toList();
    }
}
