package com.coachrun.entity;

import com.coachrun.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Étape entre « mot de passe correct » et « session ouverte » pour un compte soumis à la double
 * authentification. Le jeton remis au navigateur n'est stocké que haché.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "mfa_challenges")
public class MfaChallenge extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    /** Premier enrôlement : secret proposé, en attente d'un premier code qui prouve qu'il est bien enregistré. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "pending_secret")
    private String pendingSecret;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;
}
