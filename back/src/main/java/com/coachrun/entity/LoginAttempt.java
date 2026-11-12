package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Échecs de connexion récents sur un compte. La clé est l'empreinte SHA-256 de l'adresse saisie
 * (normalisée) : le compteur suit la cible sans conserver d'adresse en clair, y compris pour les
 * adresses inventées d'un bourrage d'identifiants.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @Column(name = "account_key", length = 64, nullable = false)
    private String accountKey;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "last_failure_at", nullable = false)
    private Instant lastFailureAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;
}
