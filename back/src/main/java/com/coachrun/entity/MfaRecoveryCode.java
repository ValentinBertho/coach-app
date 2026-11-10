package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Code de secours à usage unique (téléphone perdu). Seule son empreinte SHA-256 est stockée. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "mfa_recovery_codes")
public class MfaRecoveryCode extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;
}
