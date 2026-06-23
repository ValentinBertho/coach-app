package com.coachrun.repository;

import com.coachrun.entity.ScheduledStrengthSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduledStrengthSessionRepository extends JpaRepository<ScheduledStrengthSession, UUID> {

    List<ScheduledStrengthSession> findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
            UUID clubId, UUID athleteId, LocalDate from, LocalDate to);

    Optional<ScheduledStrengthSession> findByIdAndClubId(UUID id, UUID clubId);

    // --- Portail athlète (scoping par athleteId du principal) ---
    List<ScheduledStrengthSession> findByAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
            UUID athleteId, LocalDate from, LocalDate to);

    Optional<ScheduledStrengthSession> findByIdAndAthleteId(UUID id, UUID athleteId);
}
