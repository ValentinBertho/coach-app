package com.coachrun.repository;

import com.coachrun.entity.MfaChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MfaChallenge> findByTokenHash(String tokenHash);

    /** Lecture sans verrou : l'appelant garde sa transaction ouverte pendant la vérification. */
    @Query("select c.userId from MfaChallenge c where c.tokenHash = :hash and c.expiresAt > :now")
    Optional<UUID> findLiveOwner(@Param("hash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("delete from MfaChallenge c where c.userId = :userId")
    int deleteByUser(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from MfaChallenge c where c.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
