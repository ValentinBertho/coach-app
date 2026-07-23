package com.coachrun.repository;

import com.coachrun.entity.TrainingZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingZoneRepository extends JpaRepository<TrainingZone, UUID> {

    List<TrainingZone> findByClubIdOrderBySortOrderAscNameAsc(UUID clubId);

    Optional<TrainingZone> findByIdAndClubId(UUID id, UUID clubId);

    boolean existsByClubId(UUID clubId);
}
