package com.coachrun.repository;

import com.coachrun.entity.RaceObjective;
import com.coachrun.entity.enums.RaceObjectiveStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceObjectiveRepository extends JpaRepository<RaceObjective, UUID> {

    List<RaceObjective> findByClubIdAndAthleteIdOrderByRaceDateAsc(UUID clubId, UUID athleteId);

    Optional<RaceObjective> findByIdAndClubId(UUID id, UUID clubId);

    Optional<RaceObjective> findFirstByAthleteIdAndStatusAndRaceDateGreaterThanEqualOrderByRaceDateAsc(
            UUID athleteId, RaceObjectiveStatus status, LocalDate from);

    List<RaceObjective> findTop5ByClubIdAndStatusAndRaceDateGreaterThanEqualOrderByRaceDateAsc(
            UUID clubId, RaceObjectiveStatus status, LocalDate from);
}
