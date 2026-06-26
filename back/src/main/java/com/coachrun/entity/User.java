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
    @Column(name = "notify_email_enabled", nullable = false)
    private boolean notifyEmailEnabled = true;

    @Column(name = "notify_push_enabled", nullable = false)
    private boolean notifyPushEnabled = true;

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
}
