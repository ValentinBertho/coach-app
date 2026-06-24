package com.coachrun.entity;

import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Connexion OAuth d'un athlète à une plateforme externe (Strava…), cf. DARI Lab.
 * Jetons chiffrés au repos (RGPD, réutilise les converters existants).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "device_connections",
        uniqueConstraints = @UniqueConstraint(name = "uq_device_athlete_provider",
                columnNames = {"athlete_id", "provider"}))
public class DeviceConnection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    private DeviceProvider provider;

    @Column(name = "provider_athlete_id", length = 64)
    private String providerAthleteId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "refresh_token", nullable = false)
    private String refreshToken;

    /** Expiration de l'access token (epoch seconds). */
    @Column(name = "expires_at", nullable = false)
    private long expiresAt;

    @Column(name = "scope", length = 255)
    private String scope;

    @Column(name = "last_import_epoch")
    private Long lastImportEpoch;
}
