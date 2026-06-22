package com.coachrun.repository;

import com.coachrun.entity.AthletePerformance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AthletePerformanceRepository extends JpaRepository<AthletePerformance, UUID> {

    List<AthletePerformance> findByAthleteIdOrderByDateSetDescCreatedAtDesc(UUID athleteId);

    Optional<AthletePerformance> findByIdAndAthleteId(UUID id, UUID athleteId);
}
