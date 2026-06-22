package com.coachrun.repository;

import com.coachrun.entity.TrainingGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingGroupRepository extends JpaRepository<TrainingGroup, UUID> {

    List<TrainingGroup> findByClubIdOrderByNameAsc(UUID clubId);

    Optional<TrainingGroup> findByIdAndClubId(UUID id, UUID clubId);
}
