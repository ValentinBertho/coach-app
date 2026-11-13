package com.coachrun.repository;

import com.coachrun.entity.GarminWorkoutSync;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GarminWorkoutSyncRepository extends JpaRepository<GarminWorkoutSync, UUID> {

    List<GarminWorkoutSync> findByAthleteId(UUID athleteId);

    /** Séances à venir effectivement présentes chez Garmin, pour l'écran Synchronisation. */
    long countByAthleteIdAndGarminWorkoutIdIsNotNullAndScheduledDateGreaterThanEqual(UUID athleteId, LocalDate from);

    /** Envois en échec à venir : l'écran doit le dire plutôt que laisser attendre. */
    long countByAthleteIdAndLastErrorIsNotNullAndScheduledDateGreaterThanEqual(UUID athleteId, LocalDate from);
}
