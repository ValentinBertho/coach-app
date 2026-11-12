package com.coachrun.repository;

import com.coachrun.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {

    /**
     * Création stricte (INSERT, jamais de fusion) : un {@code save()} sur une clé déjà présente
     * ferait un merge et remettrait à zéro un compteur que d'autres tentatives viennent d'incrémenter.
     */
    @Modifying
    @Query(value = "insert into login_attempts (account_key, failure_count, last_failure_at) "
            + "values (:key, 0, :now)", nativeQuery = true)
    int insertEmpty(@Param("key") String key, @Param("now") Instant now);

    /** Un compteur sans échec récent repart de zéro (avant l'incrément, sous le même verrou). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LoginAttempt a set a.failureCount = 0, a.lockedUntil = null "
            + "where a.accountKey = :key and a.lastFailureAt < :cutoff")
    int resetIfStale(@Param("key") String key, @Param("cutoff") Instant cutoff);

    /**
     * Incrément atomique, fait par la base : aucune lecture-modification-écriture côté Java, donc
     * aucun échec perdu quand plusieurs tentatives arrivent en même temps. L'UPDATE verrouille la
     * ligne jusqu'à la fin de la transaction.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LoginAttempt a set a.failureCount = a.failureCount + 1, a.lastFailureAt = :now "
            + "where a.accountKey = :key")
    int increment(@Param("key") String key, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LoginAttempt a set a.lockedUntil = :until where a.accountKey = :key")
    int lockUntil(@Param("key") String key, @Param("until") Instant until);

    @Modifying
    @Query("delete from LoginAttempt a where a.lastFailureAt < :cutoff")
    int deleteStaleBefore(@Param("cutoff") Instant cutoff);
}
