package com.coachrun.repository;

import com.coachrun.entity.Athlete1rmProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Athlete1rmProfileRepository extends JpaRepository<Athlete1rmProfile, UUID> {

    List<Athlete1rmProfile> findByAthleteId(UUID athleteId);

    Optional<Athlete1rmProfile> findByAthleteIdAndExerciseId(UUID athleteId, UUID exerciseId);
}
