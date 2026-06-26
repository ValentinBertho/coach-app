package com.coachrun.repository;

import com.coachrun.entity.CalendarNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarNoteRepository extends JpaRepository<CalendarNote, UUID> {

    List<CalendarNote> findByClubIdAndAthleteIdAndNoteDateBetweenOrderByNoteDateAsc(
            UUID clubId, UUID athleteId, LocalDate from, LocalDate to);

    List<CalendarNote> findByAthleteIdAndNoteDateBetweenOrderByNoteDateAsc(
            UUID athleteId, LocalDate from, LocalDate to);

    Optional<CalendarNote> findByIdAndClubId(UUID id, UUID clubId);
}
