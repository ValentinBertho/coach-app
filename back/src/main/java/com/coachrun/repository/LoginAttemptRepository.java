package com.coachrun.repository;

import com.coachrun.entity.LoginAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoginAttempt a where a.accountKey = :key")
    Optional<LoginAttempt> lockByKey(@Param("key") String key);

    @Modifying
    @Query("delete from LoginAttempt a where a.lastFailureAt < :cutoff")
    int deleteStaleBefore(@Param("cutoff") Instant cutoff);
}
