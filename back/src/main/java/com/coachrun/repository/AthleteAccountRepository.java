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
     * club autre que son espace personnel (il appartient alors à un coach), lien de vérification encore valable, connexion récente
     * (un athlète qui a changé d'adresse et s'en sert encore), demande de coaching déposée, ou
     * signalement de fiche à son nom (la file de modération le référence).</p>
     */
    @Query("""
            SELECT u.id FROM AthleteAccount a JOIN a.user u LEFT JOIN u.club c
            WHERE u.emailVerified = false
              AND (c IS NULL OR c.personal = true)
              AND u.createdAt < :cutoff
              AND (u.verifyExpiresAt IS NULL OR u.verifyExpiresAt < :now)
              AND (u.lastLoginAt IS NULL OR u.lastLoginAt < :cutoff)
              AND NOT EXISTS (SELECT 1 FROM CoachingRequest r WHERE r.athleteAccount = a)
              AND NOT EXISTS (SELECT 1 FROM CoachProfileReport p WHERE p.reporter = u)
            """)
    List<UUID> findAbandonedUnverifiedUserIds(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

    /** Les espaces personnels du compte : ils partent avec lui. */
    @Query("""
            SELECT DISTINCT ath.club.id FROM Athlete ath
            WHERE ath.account.user.id = :userId AND ath.club.personal = true
            """)
    List<UUID> findPersonalClubIdsOfUser(@Param("userId") UUID userId);

    /** Décroche le compte de sa fiche et de son espace, pour que l'espace puisse partir. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE users SET athlete_id = NULL, club_id = NULL WHERE id = :userId", nativeQuery = true)
    int detachUserNative(@Param("userId") UUID userId);

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
