package com.coachrun.repository;

import com.coachrun.entity.Activity;
import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.ActivityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Optional<Activity> findByIdAndClubId(UUID id, UUID clubId);

    List<Activity> findByClubIdAndAthleteIdOrderByActivityDateDesc(UUID clubId, UUID athleteId);

    List<Activity> findByAthleteIdOrderByActivityDateDesc(UUID athleteId);

    /** Activités d'une fenêtre — durées mesurées pour le calcul de charge (une requête, pas N). */
    List<Activity> findByAthleteIdAndActivityDateBetween(UUID athleteId, java.time.LocalDate from, java.time.LocalDate to);

    /** Les activités d'un ensemble d'athlètes sur une plage de dates — le bilan de la semaine. */
    List<Activity> findByAthleteIdInAndActivityDateBetween(Collection<UUID> athleteIds,
                                                           java.time.LocalDate from,
                                                           java.time.LocalDate to);

    /** Activité réalisée rapprochée d'une séance planifiée (vue « réalisé » de la fiche séance). */
    Optional<Activity> findByClubIdAndAthleteIdAndMatchedWorkoutId(UUID clubId, UUID athleteId, UUID workoutId);

    /** Même lecture depuis le portail athlète, qui n'a que son propre identifiant à porter. */
    Optional<Activity> findByAthleteIdAndMatchedWorkoutId(UUID athleteId, UUID workoutId);

    /** Déduplication des imports (cf. contrainte UNIQUE athlete/source/external_id). */
    boolean existsByAthleteIdAndSourceAndExternalId(UUID athleteId, ActivitySource source, String externalId);

    /**
     * Activités rapprochées d'une séance et jamais encore proposées au ressenti, les plus
     * récentes d'abord. Le rapprochement clôture la séance côté données mais laisse le ressenti
     * vide : ce sont exactement les séances dont le coach n'a aucun signal de forme.
     */
    List<Activity> findByAthleteIdAndStatusAndMatchedWorkoutIdIsNotNullAndFeedbackPromptedAtIsNullOrderByActivityDateDesc(
            UUID athleteId, ActivityStatus status);

    /**
     * Sorties jamais proposées au débrief, la plus récente d'abord — rattachées ou non.
     *
     * <p>La requête précédente exigeait un rapprochement avec une séance : une course, un
     * footing improvisé, tout ce qui n'était au programme de personne n'était donc jamais
     * proposé au ressenti. C'est pourtant là que le coach n'a rien d'autre à lire.</p>
     */
    List<Activity> findByAthleteIdAndFeedbackPromptedAtIsNullOrderByActivityDateDesc(UUID athleteId);

    /**
     * Parmi ces séances, celles qui portent déjà une activité — donc à écarter d'un nouveau
     * rapprochement automatique, sous peine de voler la sortie déjà rattachée.
     *
     * <p>Cette exclusion était jusqu'ici un effet de bord : le rapprochement faisait passer la
     * séance hors de {@code PLANNED}, et le tri ne regardait que {@code PLANNED}. Le jour où une
     * séance notée par l'athlète est redevenue rapprochable, l'effet de bord a disparu avec
     * elle — d'où cette question, désormais posée pour ce qu'elle est.</p>
     */
    @Query("select a.matchedWorkoutId from Activity a where a.matchedWorkoutId in :workoutIds")
    List<UUID> findMatchedWorkoutIdsIn(@Param("workoutIds") Collection<UUID> workoutIds);

    // --- Administration (cross-club) ---
    long countByActivityDateAfter(java.time.LocalDate date);

    long countByClubIdAndActivityDateAfter(UUID clubId, java.time.LocalDate date);

    long countByClubId(UUID clubId);

    /** Dernière sortie enregistrée dans un club : dit d'un coup d'œil s'il est encore vivant. */
    @Query("select max(a.activityDate) from Activity a where a.club.id = :clubId")
    java.util.Optional<java.time.LocalDate> findLastActivityDate(@Param("clubId") UUID clubId);

    @Query("select max(a.activityDate) from Activity a where a.athlete.id = :athleteId")
    java.util.Optional<java.time.LocalDate> findLastActivityDateForAthlete(
            @Param("athleteId") UUID athleteId);
}
