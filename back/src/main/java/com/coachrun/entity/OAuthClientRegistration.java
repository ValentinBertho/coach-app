package com.coachrun.entity;

import com.coachrun.entity.enums.DeviceProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Identifiant client OAuth obtenu par enregistrement dynamique (RFC 7591) auprès d'un serveur
 * régional d'un fournisseur — aujourd'hui COROS, qui n'en attribue pas autrement.
 *
 * <p>Un client par (fournisseur, serveur, adresse de retour) : s'enregistrer à chaque connexion
 * multiplierait les clients chez COROS sans rien apporter.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "oauth_client_registrations",
        uniqueConstraints = @UniqueConstraint(name = "uq_oauth_client_registration",
                columnNames = {"provider", "issuer", "redirect_uri"}))
public class OAuthClientRegistration extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    private DeviceProvider provider;

    @Column(name = "issuer", nullable = false, length = 255)
    private String issuer;

    @Column(name = "redirect_uri", nullable = false, length = 255)
    private String redirectUri;

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;
}
