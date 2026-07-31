package com.coachrun.repository;

import com.coachrun.entity.Activity;
import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.ActivityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Optional<Activity> findByIdAndClubId(UUID id, UUID clubId);

    List<Activity> findByClubIdAndAthleteIdOrderByActivityDateDesc(UUID clubId, UUID athleteId);

    List<Activity> findByAthleteIdOrderByActivityDateDesc(UUID athleteId);

    /** Activité réalisée rapprochée d'une séance planifiée (vue « réalisé » de la fiche séance). */
    Optional<Activity> findByClubIdAndAthleteIdAndMatchedWorkoutId(UUID clubId, UUID athleteId, UUID workoutId);

    /** Déduplication des imports (cf. contrainte UNIQUE athlete/source/external_id). */
    boolean existsByAthleteIdAndSourceAndExternalId(UUID athleteId, ActivitySource source, String externalId);

    /**
     * Activités rapprochées d'une séance et jamais encore proposées au ressenti, les plus
     * récentes d'abord. Le rapprochement clôture la séance côté données mais laisse le ressenti
     * vide : ce sont exactement les séances dont le coach n'a aucun signal de forme.
     */
    List<Activity> findByAthleteIdAndStatusAndMatchedWorkoutIdIsNotNullAndFeedbackPromptedAtIsNullOrderByActivityDateDesc(
            UUID athleteId, ActivityStatus status);
}
