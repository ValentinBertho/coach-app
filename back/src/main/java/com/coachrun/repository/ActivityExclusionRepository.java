package com.coachrun.repository;

import com.coachrun.entity.ActivityExclusion;
import com.coachrun.entity.enums.ActivitySource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityExclusionRepository extends JpaRepository<ActivityExclusion, UUID> {

    boolean existsByAthleteIdAndSourceAndExternalId(UUID athleteId, ActivitySource source,
                                                    String externalId);

    /** Les sorties écartées, la plus récemment masquée d'abord — l'ordre où on vient les relire. */
    List<ActivityExclusion> findByAthleteIdOrderByCreatedAtDesc(UUID athleteId);

    Optional<ActivityExclusion> findByIdAndAthleteId(UUID id, UUID athleteId);
}
