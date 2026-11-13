package com.coachrun.repository;

import com.coachrun.entity.OAuthClientRegistration;
import com.coachrun.entity.enums.DeviceProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthClientRegistrationRepository extends JpaRepository<OAuthClientRegistration, UUID> {

    Optional<OAuthClientRegistration> findByProviderAndIssuerAndRedirectUri(
            DeviceProvider provider, String issuer, String redirectUri);
}
