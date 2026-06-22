package com.coachrun.repository;

import com.coachrun.entity.TrainingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, UUID> {

    List<TrainingPlan> findByClubIdOrderByNameAsc(UUID clubId);

    Optional<TrainingPlan> findByIdAndClubId(UUID id, UUID clubId);

    /** Plans attribués à un athlète (relation many-to-many plan ↔ athlètes). */
    List<TrainingPlan> findByAthletes_IdOrderByNameAsc(UUID athleteId);
}
