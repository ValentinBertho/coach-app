package com.coachrun.repository;

import com.coachrun.entity.Workout;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutRepository extends JpaRepository<Workout, UUID> {

    /** Plage de dates d'un athlète (calendrier), étapes incluses. Scoping tenant. */
    @EntityGraph(attributePaths = "steps")
    List<Workout> findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
            UUID clubId, UUID athleteId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = "steps")
    Optional<Workout> findByIdAndClubId(UUID id, UUID clubId);
}
