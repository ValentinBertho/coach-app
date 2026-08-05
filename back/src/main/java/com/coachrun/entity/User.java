package com.coachrun.entity;

import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Compte utilisateur (coach, head coach, athlète, admin). Rattaché à un club
 * (tenant), sauf PLATFORM_ADMIN. Le scoping multi-tenant se fait par {@code clubId}.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UserStatus status = UserStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    /**
     * Clubs additionnels du coach (en plus du club principal {@link #club}).
     * Ouverture many-to-many : un coach peut intervenir dans plusieurs clubs.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_clubs",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "club_id"))
    private Set<Club> additionalClubs = new HashSet<>();

    /** Présent uniquement pour les comptes ATHLETE (onboarding par lien magique). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "athlete_id")
    private Athlete athlete;

    /** Invitation coach (lien magique) : jeton et expiration ; nuls une fois acceptée. */
    @Column(name = "invite_token", length = 64, unique = true)
    private String inviteToken;

    @Column(name = "invite_expires_at")
    private java.time.Instant inviteExpiresAt;

    /** Préférences de notification (l'in-app reste toujours actif). */
    /** Unité d'affichage des allures (préférence personnelle, pas un réglage club). */
    @Enumerated(EnumType.STRING)
    @Column(name = "pace_unit", nullable = false, length = 8)
    private com.coachrun.entity.enums.PaceUnit paceUnit = com.coachrun.entity.enums.PaceUnit.PACE;

    @Column(name = "notify_email_enabled", nullable = false)
    private boolean notifyEmailEnabled = true;

    @Column(name = "notify_push_enabled", nullable = false)
    private boolean notifyPushEnabled = true;

    /**
     * Heure habituelle de séance de l'athlète : point d'ancrage du rappel « Ta séance est
     * finie ? », envoyé 2 h après. Nulle = pas de rappel de débriefing (opt-out par l'heure).
     */
    @Column(name = "usual_session_time")
    private java.time.LocalTime usualSessionTime = java.time.LocalTime.of(18, 0);

    /**
     * Catégories dont le <b>push</b> est coupé, en liste séparée par des virgules
     * ({@code PROGRAMME,MESSAGES}). Vide = tout passe, ce qui est le réglage par défaut.
     *
     * <p>Une colonne unique plutôt qu'un booléen par famille : ajouter une catégorie ne demande
     * alors ni migration ni colonne morte pour les comptes existants. Le format est un détail
     * d'entreposage — {@link #mutedCategories()} et {@link #setMutedCategories} sont la seule
     * porte d'entrée, et l'énumération reste la source de vérité.</p>
     */
    @Column(name = "notify_muted_categories", length = 255)
    private String notifyMutedCategories;

    /**
     * Début et fin des heures de silence, dans le fuseau de l'application. Pendant cette plage,
     * aucune notification système n'est émise — le centre de notifications, lui, continue de se
     * remplir : on retire l'interruption, jamais l'information.
     *
     * <p>Réglé par défaut sur 22 h – 7 h. Un commentaire de coach écrit à 23 h 40 faisait vibrer
     * le téléphone de son athlète, ce qu'aucune des deux parties ne demandait. Mettre les deux
     * bornes à la même heure désactive le silence.</p>
     */
    @Column(name = "notify_quiet_start")
    private java.time.LocalTime notifyQuietStart = java.time.LocalTime.of(22, 0);

    @Column(name = "notify_quiet_end")
    private java.time.LocalTime notifyQuietEnd = java.time.LocalTime.of(7, 0);

    /** Catégories coupées, décodées. Jamais {@code null}. */
    public java.util.Set<com.coachrun.entity.enums.NotificationCategory> mutedCategories() {
        if (notifyMutedCategories == null || notifyMutedCategories.isBlank()) {
            return java.util.EnumSet.noneOf(com.coachrun.entity.enums.NotificationCategory.class);
        }
        java.util.Set<com.coachrun.entity.enums.NotificationCategory> out =
                java.util.EnumSet.noneOf(com.coachrun.entity.enums.NotificationCategory.class);
        for (String raw : notifyMutedCategories.split(",")) {
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            try {
                out.add(com.coachrun.entity.enums.NotificationCategory.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // Catégorie retirée du produit depuis : la préférence n'a plus d'objet.
            }
        }
        return out;
    }

    /** Remplace les catégories coupées (l'ordre de l'énumération rend la valeur stable en base). */
    public void setMutedCategories(java.util.Set<com.coachrun.entity.enums.NotificationCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            this.notifyMutedCategories = null;
            return;
        }
        this.notifyMutedCategories = java.util.EnumSet.copyOf(categories).stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.joining(","));
    }

    /** Réinitialisation de mot de passe : jeton et expiration ; nuls une fois utilisé. */
    @Column(name = "reset_token", length = 64, unique = true)
    private String resetToken;

    @Column(name = "reset_expires_at")
    private java.time.Instant resetExpiresAt;

    /**
     * Vérification d'e-mail (inscription coach). Vrai par défaut (comptes seedés / athlètes via
     * lien magique) ; passé à faux à l'inscription jusqu'à confirmation du lien.
     */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = true;

    @Column(name = "verify_token", length = 64, unique = true)
    private String verifyToken;

    @Column(name = "verify_expires_at")
    private java.time.Instant verifyExpiresAt;

    /** Preuve de consentement RGPD : acceptation des CGU / politique de confidentialité. */
    @Column(name = "terms_accepted_at")
    private java.time.Instant termsAcceptedAt;

    /**
     * Date du dernier changement de mot de passe. Tout jeton (accès ou rafraîchissement) émis
     * avant est rejeté : sans ça, un reset ne chassait pas la session qu'il était censé chasser —
     * le refresh token vole pendant 30 jours.
     */
    @Column(name = "password_changed_at")
    private java.time.Instant passwordChangedAt;

    /**
     * Date de la dernière révocation volontaire des sessions (déconnexion).
     *
     * <p>Même rôle que {@link #passwordChangedAt}, pour un geste différent : la déconnexion ne
     * révoquait que l'access token, via une liste noire en mémoire. Le refresh token restait
     * valable trente jours côté serveur — seule sa copie locale disparaissait — et un
     * redéploiement vidait la liste noire, ressuscitant tous les jetons déjà rotés.</p>
     *
     * <p>Conséquence assumée : se déconnecter ferme la session sur <b>tous</b> les appareils du
     * compte. C'est le comportement sûr, et le seul qui tienne sans stocker un jeton par
     * appareil.</p>
     */
    @Column(name = "sessions_invalidated_at")
    private java.time.Instant sessionsInvalidatedAt;
}
