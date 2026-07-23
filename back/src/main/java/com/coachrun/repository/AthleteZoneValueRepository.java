package com.coachrun.repository;

import com.coachrun.entity.AthleteZoneValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AthleteZoneValueRepository extends JpaRepository<AthleteZoneValue, UUID> {

    List<AthleteZoneValue> findByAthleteId(UUID athleteId);

    Optional<AthleteZoneValue> findByAthleteIdAndZoneIdAndMetricTypeId(
            UUID athleteId, UUID zoneId, UUID metricTypeId);

    boolean existsByAthleteId(UUID athleteId);
}
