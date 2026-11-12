package com.coachrun.entity;

import com.coachrun.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Second facteur (TOTP) d'un compte. Le secret est chiffré au repos : qui lit la table sans la
 * clé ne peut pas générer de codes.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "mfa_credentials")
public class MfaCredential {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Secret TOTP en base32, chiffré par {@link EncryptedStringConverter}. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "secret", nullable = false)
    private String secret;

    @Column(name = "enabled_at", nullable = false)
    private Instant enabledAt;

    /** Dernier pas de 30 s accepté : un code déjà présenté ne repasse pas, même dans sa fenêtre. */
    @Column(name = "last_step", nullable = false)
    private long lastStep;
}
