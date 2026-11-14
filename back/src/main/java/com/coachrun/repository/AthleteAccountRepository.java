package com.coachrun.repository;

import com.coachrun.entity.AthleteAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AthleteAccountRepository extends JpaRepository<AthleteAccount, UUID> {

    Optional<AthleteAccount> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    /**
     * Comptes d'athlète inscrits seuls et <b>jamais confirmés</b>, abandonnés depuis {@code cutoff}.
     *
     * <p>Chaque condition écarte un compte qu'on ne doit pas toucher : rattaché à une fiche ou à un
     * club (il appartient alors à un coach), lien de vérification encore valable, connexion récente
     * (un athlète qui a changé d'adresse et s'en sert encore), demande de coaching déposée, ou
     * signalement de fiche à son nom (la file de modération le référence).</p>
     */
    @Query("""
            SELECT u.id FROM AthleteAccount a JOIN a.user u
            WHERE u.emailVerified = false
              AND u.athlete IS NULL
              AND u.club IS NULL
              AND u.createdAt < :cutoff
              AND (u.verifyExpiresAt IS NULL OR u.verifyExpiresAt < :now)
              AND (u.lastLoginAt IS NULL OR u.lastLoginAt < :cutoff)
              AND NOT EXISTS (SELECT 1 FROM CoachingRequest r WHERE r.athleteAccount = a)
              AND NOT EXISTS (SELECT 1 FROM CoachProfileReport p WHERE p.reporter = u)
            """)
    List<UUID> findAbandonedUnverifiedUserIds(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM push_subscriptions WHERE user_id = :userId", nativeQuery = true)
    int deletePushSubscriptionsOf(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM athlete_accounts WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserIdNative(@Param("userId") UUID userId);

    /** Le compte lui-même ; jetons, notifications et lectures partent en cascade (base). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM users WHERE id = :userId", nativeQuery = true)
    int deleteUserNative(@Param("userId") UUID userId);
}
