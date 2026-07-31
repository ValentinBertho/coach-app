package com.coachrun.repository;

import com.coachrun.entity.WellnessCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WellnessCheckInRepository extends JpaRepository<WellnessCheckIn, UUID> {

    Optional<WellnessCheckIn> findByAthleteIdAndCheckDate(UUID athleteId, LocalDate checkDate);

    /** Dernier check-in connu — la source « avant séance » de l'état de forme. */
    Optional<WellnessCheckIn> findFirstByAthleteIdOrderByCheckDateDesc(UUID athleteId);

    List<WellnessCheckIn> findByAthleteIdAndCheckDateBetweenOrderByCheckDateAsc(
            UUID athleteId, LocalDate from, LocalDate to);
}
