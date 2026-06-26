package com.coachrun.repository;

import com.coachrun.entity.PlanAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanAssignmentRepository extends JpaRepository<PlanAssignment, UUID> {

    Optional<PlanAssignment> findByPlanIdAndAthleteId(UUID planId, UUID athleteId);

    void deleteByPlanIdAndAthleteId(UUID planId, UUID athleteId);
}
