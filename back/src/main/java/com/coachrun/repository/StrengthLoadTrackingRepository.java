package com.coachrun.repository;

import com.coachrun.entity.StrengthLoadTracking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface StrengthLoadTrackingRepository extends JpaRepository<StrengthLoadTracking, UUID> {

    List<StrengthLoadTracking> findByAthleteIdAndSessionDateBetweenOrderBySessionDateAsc(
            UUID athleteId, LocalDate from, LocalDate to);

    List<StrengthLoadTracking> findByAthleteIdOrderBySessionDateAsc(UUID athleteId);
}
