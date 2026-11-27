package com.coachrun.repository;

import com.coachrun.entity.StrengthTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StrengthTestRepository extends JpaRepository<StrengthTest, UUID> {

    List<StrengthTest> findByAthleteIdOrderByTestDateDesc(UUID athleteId);

    List<StrengthTest> findByAthleteIdIn(java.util.Collection<UUID> athleteIds);

    List<StrengthTest> findByAthleteIdAndExerciseIdOrderByTestDateDesc(UUID athleteId, UUID exerciseId);
}
