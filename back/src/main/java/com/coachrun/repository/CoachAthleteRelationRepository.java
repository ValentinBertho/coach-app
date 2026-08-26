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

    /**
     * Athlètes actifs qu'<b>aucun coach</b> ne suit.
     *
     * <p>C'est {@code CoachAthleteRelation} qui fait foi, pas la table {@code athlete_coaches} :
     * cette dernière ne porte que des rattachements <i>additionnels</i>, en plus de l'accès
     * implicite des coachs du club. La compter aurait fait passer pour « sans coach » la quasi-
     * totalité des athlètes de club — un signal qui crie tout le temps ne se lit plus.</p>
     */
    @Query("""
            select count(a) from Athlete a
            where a.status = com.coachrun.entity.enums.AthleteStatus.ACTIVE
              and not exists (
                  select 1 from CoachAthleteRelation r
                  where r.athlete = a and r.active = true)
              and a.coaches is empty
            """)
    long countActiveAthletesWithoutAnyCoach();

    /** Nombre de coachs actifs par athlète, pour une page de liste (une requête, pas N). */
    @Query("""
            select r.athlete.id, count(r) from CoachAthleteRelation r
            where r.athlete.id in :athleteIds and r.active = true
            group by r.athlete.id
            """)
    List<Object[]> countActiveByAthleteIds(
            @org.springframework.data.repository.query.Param("athleteIds")
            java.util.Collection<UUID> athleteIds);
}
