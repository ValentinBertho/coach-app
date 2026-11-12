package com.coachrun.repository;

import com.coachrun.entity.MfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {

    /** Consomme le code de façon atomique : un seul des deux usages simultanés l'obtient. */
    @Modifying
    @Query("update MfaRecoveryCode r set r.usedAt = :now "
            + "where r.userId = :userId and r.codeHash = :hash and r.usedAt is null")
    int consume(@Param("userId") UUID userId, @Param("hash") String hash, @Param("now") Instant now);

    long countByUserIdAndUsedAtIsNull(UUID userId);

    @Modifying
    @Query("delete from MfaRecoveryCode r where r.userId = :userId")
    int deleteByUser(@Param("userId") UUID userId);
}
