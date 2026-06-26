package com.coachrun.repository;

import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutRepository extends JpaRepository<Workout, UUID> {

    /** Job de rappel J-1 (tous clubs). Athlète chargé pour l'email. */
    @EntityGraph(attributePaths = "athlete")
    List<Workout> findByScheduledDateAndStatus(LocalDate date, WorkoutStatus status);

    /** Plage de dates d'un athlète (calendrier), étapes incluses. Scoping tenant. */
    @EntityGraph(attributePaths = "steps")
    List<Workout> findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
            UUID clubId, UUID athleteId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = "steps")
    Optional<Workout> findByIdAndClubId(UUID id, UUID clubId);

    // --- Portail athlète (scoping par athleteId du principal) ---
    @EntityGraph(attributePaths = "steps")
    List<Workout> findByAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
            UUID athleteId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = "steps")
    List<Workout> findByAthleteIdAndScheduledDateOrderByCreatedAtAsc(UUID athleteId, LocalDate date);

    @EntityGraph(attributePaths = "steps")
    Optional<Workout> findByIdAndAthleteId(UUID id, UUID athleteId);

    @EntityGraph(attributePaths = "steps")
    List<Workout> findByAthleteIdOrderByScheduledDateAsc(UUID athleteId);

    long countByClubIdAndStatusAndScheduledDateLessThan(UUID clubId, WorkoutStatus status, LocalDate date);

    long countByClubIdAndStatusAndScheduledDateBetween(UUID clubId, WorkoutStatus status, LocalDate from, LocalDate to);

    /** Dernier retour renseigné (fatigue/douleur) d'un athlète — base de l'état de forme. */
    Optional<Workout> findFirstByAthleteIdAndFatigueIsNotNullOrderByScheduledDateDescCreatedAtDesc(UUID athleteId);

    // --- Suivi de plan (séances liées via plan_id) ---
    long countByPlanIdAndAthleteId(UUID planId, UUID athleteId);

    long countByPlanIdAndAthleteIdAndStatus(UUID planId, UUID athleteId, WorkoutStatus status);

    /** Suppression propre : retire les séances d'un plan encore planifiées (préserve l'historique réalisé). */
    void deleteByPlanIdAndAthleteIdAndStatus(UUID planId, UUID athleteId, WorkoutStatus status);
}
