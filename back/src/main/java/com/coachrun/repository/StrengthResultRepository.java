package com.coachrun.repository;

import com.coachrun.entity.StrengthResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StrengthResultRepository extends JpaRepository<StrengthResult, UUID> {

    List<StrengthResult> findByScheduledSessionIdOrderByExerciseIdAscSetNumberAsc(UUID scheduledSessionId);

    List<StrengthResult> findByAthleteIdAndExerciseIdOrderByCreatedAtDesc(UUID athleteId, UUID exerciseId);
}
