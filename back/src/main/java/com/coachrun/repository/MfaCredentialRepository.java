package com.coachrun.repository;

import com.coachrun.entity.MfaCredential;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MfaCredentialRepository extends JpaRepository<MfaCredential, UUID> {

    /** Verrou : deux vérifications simultanées du même code ne peuvent pas réussir toutes les deux. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MfaCredential c where c.userId = :userId")
    Optional<MfaCredential> findForUpdate(@Param("userId") UUID userId);
}
