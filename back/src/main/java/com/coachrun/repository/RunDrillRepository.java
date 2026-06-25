package com.coachrun.repository;

import com.coachrun.entity.RunDrill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RunDrillRepository extends JpaRepository<RunDrill, UUID> {
    List<RunDrill> findByClubIdOrderByCategoryAscNameAsc(UUID clubId);
    Optional<RunDrill> findByIdAndClubId(UUID id, UUID clubId);
}
