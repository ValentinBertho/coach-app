package com.coachrun.repository;

import com.coachrun.entity.AthleteVdotPace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AthleteVdotPaceRepository extends JpaRepository<AthleteVdotPace, UUID> {

    Optional<AthleteVdotPace> findByAthleteId(UUID athleteId);
}
