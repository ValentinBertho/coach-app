package com.coachrun.repository;

import com.coachrun.entity.StrengthCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StrengthCycleRepository extends JpaRepository<StrengthCycle, UUID> {

    List<StrengthCycle> findByClubIdOrderByName(UUID clubId);

    Optional<StrengthCycle> findByIdAndClubId(UUID id, UUID clubId);
}
