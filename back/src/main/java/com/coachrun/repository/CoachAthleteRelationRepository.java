package com.coachrun.repository;

import com.coachrun.entity.CoachAthleteRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachAthleteRelationRepository extends JpaRepository<CoachAthleteRelation, UUID> {

    /** Relation active d'un coach donné sur un athlète donné (référent ou non). */
    Optional<CoachAthleteRelation> findByCoachIdAndAthleteIdAndActiveTrue(UUID coachId, UUID athleteId);

    /** Relation référente active de l'athlète : porte le rattachement privé/club. */
    Optional<CoachAthleteRelation> findByAthleteIdAndReferentTrueAndActiveTrue(UUID athleteId);

    List<CoachAthleteRelation> findByAthleteIdAndActiveTrue(UUID athleteId);

    List<CoachAthleteRelation> findByCoachIdAndActiveTrue(UUID coachId);
}
