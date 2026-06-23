package com.coachrun.repository;

import com.coachrun.entity.EstimatedOneRm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EstimatedOneRmRepository extends JpaRepository<EstimatedOneRm, UUID> {

    List<EstimatedOneRm> findByAthleteIdAndExerciseIdOrderByCreatedAtAsc(UUID athleteId, UUID exerciseId);
}
