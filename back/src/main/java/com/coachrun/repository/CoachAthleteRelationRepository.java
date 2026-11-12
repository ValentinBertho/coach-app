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

    /**
     * Identifiants des athlètes ayant <b>déjà eu</b> une relation référente — active ou close.
     *
     * <p>C'est la clé d'idempotence du backfill multi-coach, et elle ne filtre volontairement pas
     * sur {@code active}. Elle l'a fait, et c'était un piège : le backfill s'exécutant à chaque
     * démarrage, un athlète dont la relation venait d'être close réapparaissait « sans référent »
     * au déploiement suivant. Selon qui était le coach détaché, la suite était mauvaise de deux
     * façons — et la première est la pire :</p>
     * <ul>
     *   <li>si c'était le <b>head coach du club</b> — le cas nominal d'un coach indépendant, dont
     *       le club porte la fiche de tous ses athlètes — le backfill réinsérait la même paire
     *       (coach, athlète) et violait {@code uq_coach_athlete}. Comme il s'exécute dans un
     *       {@code ApplicationRunner}, l'exception <b>empêchait l'application de démarrer</b> ;</li>
     *   <li>sinon, la relation était recréée au profit du head coach, à qui l'on rendait en
     *       silence un accès que personne ne lui avait accordé.</li>
     * </ul>
     * <p>Dans les deux cas, la révocation tenait jusqu'au redémarrage suivant : elle marchait
     * quand on la testait, et se défaisait au déploiement.</p>
     */
    @Query("select r.athlete.id from CoachAthleteRelation r where r.referent = true")
    Set<UUID> findAthleteIdsWithAnyReferent();

    /** Relation référente active de l'athlète : porte le rattachement privé/club. */
    Optional<CoachAthleteRelation> findByAthleteIdAndReferentTrueAndActiveTrue(UUID athleteId);

    /**
     * L'athlète a-t-il <b>déjà eu</b> un coach référent, la relation fût-elle close ?
     *
     * <p>Sans filtre sur {@code active}, et c'est tout l'objet : cette question sépare deux
     * situations que {@code AthleteAccessValidator} confondait. Un athlète antérieur au modèle
     * multi-coach n'a jamais eu de relation référente et doit rester joignable par les coachs de
     * son club (repli historique) ; un athlète dont la relation a été close ne doit plus l'être
     * par le coach qu'on vient d'en détacher. Les deux se présentaient jusqu'ici de la même
     * façon — aucune relation référente active — et le repli rendait l'écriture aux deux.</p>
     */
    boolean existsByAthleteIdAndReferentTrue(UUID athleteId);

    /**
     * La relation référente de ce coach sur cet athlète, close ou non.
     *
     * <p>Sert à reconnaître l'<b>ancien</b> coach d'un athlète : celui qui a tenu la fiche garde le
     * droit de la relire, jamais celui de l'écrire. Sans cette question, une relation close rendait
     * la fiche illisible <em>par tout le monde</em> — l'athlète étant parti et le coach forclos —
     * c'est-à-dire de la donnée morte, gardée pour personne.</p>
     */
    Optional<CoachAthleteRelation> findByCoachIdAndAthleteIdAndReferentTrue(UUID coachId, UUID athleteId);

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
