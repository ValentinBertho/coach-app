package com.coachrun.repository;

import com.coachrun.entity.CoachAthleteRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CoachAthleteRelationRepository extends JpaRepository<CoachAthleteRelation, UUID> {

    /** Relation active d'un coach donné sur un athlète donné (référent ou non). */
    Optional<CoachAthleteRelation> findByCoachIdAndAthleteIdAndActiveTrue(UUID coachId, UUID athleteId);

    /** Identifiants des athlètes ayant déjà une relation référente active (pour le backfill). */
    @Query("select r.athlete.id from CoachAthleteRelation r where r.referent = true and r.active = true")
    Set<UUID> findAthleteIdsWithActiveReferent();

    /** Relation référente active de l'athlète : porte le rattachement privé/club. */
    Optional<CoachAthleteRelation> findByAthleteIdAndReferentTrueAndActiveTrue(UUID athleteId);

    List<CoachAthleteRelation> findByAthleteIdAndActiveTrue(UUID athleteId);

    List<CoachAthleteRelation> findByCoachIdAndActiveTrue(UUID coachId);
}
