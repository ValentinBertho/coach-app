package com.coachrun.repository;

import com.coachrun.entity.Activity;
import com.coachrun.entity.enums.ActivitySource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Optional<Activity> findByIdAndClubId(UUID id, UUID clubId);

    List<Activity> findByClubIdAndAthleteIdOrderByActivityDateDesc(UUID clubId, UUID athleteId);

    /** Déduplication des imports (cf. contrainte UNIQUE athlete/source/external_id). */
    boolean existsByAthleteIdAndSourceAndExternalId(UUID athleteId, ActivitySource source, String externalId);
}
