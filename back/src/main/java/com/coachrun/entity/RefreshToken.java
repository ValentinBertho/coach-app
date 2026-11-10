package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Un jeton de rafraîchissement émis pour une session. Seule son empreinte SHA-256 est stockée :
 * une fuite de la table ne rend aucune session rejouable.
 *
 * <p>Les jetons d'une même session forment une <b>famille</b> : chaque rotation en crée un
 * nouveau et marque l'ancien {@code usedAt}. Un jeton déjà utilisé qui revient est un rejeu —
 * quelqu'un d'autre a eu le jeton entre les mains — et toute la famille est révoquée.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Rotation faite : le jeton a déjà servi une fois et ne doit plus revenir. */
    @Column(name = "used_at")
    private Instant usedAt;

    /** Session fermée (déconnexion, rejeu détecté, mot de passe changé). */
    @Column(name = "revoked_at")
    private Instant revokedAt;
}
