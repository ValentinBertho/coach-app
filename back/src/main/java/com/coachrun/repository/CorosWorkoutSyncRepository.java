package com.coachrun.repository;

import com.coachrun.entity.CorosWorkoutSync;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CorosWorkoutSyncRepository extends JpaRepository<CorosWorkoutSync, UUID> {

    List<CorosWorkoutSync> findByAthleteId(UUID athleteId);

    @Query("select count(s) from CorosWorkoutSync s where s.athleteId = :athleteId and s.cancelled = false"
            + " and s.idInPlan is not null and s.lastError is null and s.scheduledDate >= :today")
    long countOnCoros(@Param("athleteId") UUID athleteId, @Param("today") LocalDate today);

    @Query("select count(s) from CorosWorkoutSync s where s.athleteId = :athleteId and s.lastError is not null"
            + " and s.scheduledDate >= :today")
    long countInError(@Param("athleteId") UUID athleteId, @Param("today") LocalDate today);

    @Modifying
    @Query("delete from CorosWorkoutSync s where s.athleteId = :athleteId")
    void deleteByAthleteId(@Param("athleteId") UUID athleteId);
}
