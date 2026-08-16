package com.coachrun.repository;

import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutRepository extends JpaRepository<Workout, UUID> {

    /** Job de rappel J-1 (tous clubs). Athlète chargé pour l'email. */
    @EntityGraph(attributePaths = "athlete")
    List<Workout> findByScheduledDateAndStatus(LocalDate date, WorkoutStatus status);

    /**
     * Athlètes ayant un programme <b>vivant</b> sur une fenêtre : au moins une séance prévue ou
     * passée dans l'intervalle.
     *
     * <p>Sert au point de programme du soir, qui annonce aussi les journées de repos. Sans ce
     * filtre, la notification « Repos demain » partirait chaque nuit vers tout compte athlète
     * jamais entraîné — un dossier dormant, un essai abandonné — et deviendrait la nuisance qu'un
     * rappel quotidien devient toujours quand il ne dit rien à personne.</p>
     */
    @Query("select distinct w.athlete.id from Workout w where w.scheduledDate between :from and :to")
    List<UUID> findAthleteIdsWithWorkoutsBetween(@Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

    /**
     * Athlètes ayant une séance ce jour-là, <b>quel qu'en soit le statut</b>.
     *
     * <p>Le point de programme du soir ne comptait que les séances {@code PLANNED} pour décider
     * qui n'avait « rien de prévu ». Une séance déjà clôturée, écourtée ou déclarée non faite
     * reste pourtant au programme du lendemain : l'athlète recevait « Repos demain » en regard
     * d'une journée qui, sur son propre calendrier, portait bel et bien une séance.</p>
     */
    @Query("select distinct w.athlete.id from Workout w where w.scheduledDate = :date")
    List<UUID> findAthleteIdsWithWorkoutOn(@Param("date") LocalDate date);

    /** Plage de dates d'un athlète (calendrier), étapes incluses. Scoping tenant. */
    @EntityGraph(attributePaths = "steps")
    List<Workout> findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
            UUID clubId, UUID athleteId, LocalDate from, LocalDate to);

    /** Idem, mais ordonné aussi par ordre intra-jour (glisser-déposer du calendrier coach). */
    @EntityGraph(attributePaths = "steps")
    List<Workout> findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAscOrderIndexAsc(
            UUID clubId, UUID athleteId, LocalDate from, LocalDate to);

    List<Workout> findByClubIdAndAthleteIdAndScheduledDate(UUID clubId, UUID athleteId, LocalDate date);

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

    /** Même compte, restreint à un périmètre d'athlètes (KPI du cockpit coach). */
    long countByAthleteIdInAndStatusAndScheduledDateBetween(
            Collection<UUID> athleteIds, WorkoutStatus status, LocalDate from, LocalDate to);

    /** Dernier retour renseigné (fatigue/douleur) d'un athlète — base de l'état de forme. */
    Optional<Workout> findFirstByAthleteIdAndFatigueIsNotNullOrderByScheduledDateDescCreatedAtDesc(UUID athleteId);

    /**
     * File « retours à traiter » : séances porteuses d'un retour athlète (RPE, douleur ou
     * commentaire) que le coach n'a pas encore marquées comme traitées.
     *
     * <p>La requête était sans borne : tout retour jamais marqué restait dans la file
     * indéfiniment (148 lignes sur les six athlètes du jeu de démo), et la pastille de
     * navigation ne redescendait jamais sous « 9+ ». {@code since} borne la fenêtre à ce que le
     * coach peut encore traiter utilement ; au-delà, le retour reste consultable sur la séance
     * elle-même.</p>
     */
    @Query("""
            select w from Workout w
            where w.athlete.id in :athleteIds
              and w.coachReviewedAt is null
              and w.scheduledDate >= :since
              and (w.rpe is not null or w.pain is not null or w.athleteComment is not null)
            order by w.scheduledDate desc, w.createdAt desc
            """)
    List<Workout> findPendingFeedback(@Param("athleteIds") Collection<UUID> athleteIds,
                                      @Param("since") LocalDate since);

    /**
     * Les séances d'un jour donné, pour un ensemble d'athlètes — la journée du coach.
     *
     * <p>Le calendrier ne sait lire qu'un athlète à la fois : « qu'est-ce qui est prévu
     * aujourd'hui » demandait donc autant de requêtes que d'athlètes suivis. L'ordre reprend
     * celui du calendrier (rang intra-jour, puis création) pour qu'une même journée se lise
     * pareil des deux côtés.</p>
     */
    @Query("""
            select w from Workout w
            where w.athlete.id in :athleteIds
              and w.scheduledDate = :date
            order by w.orderIndex asc, w.createdAt asc
            """)
    List<Workout> findByAthletesAndDate(@Param("athleteIds") Collection<UUID> athleteIds,
                                        @Param("date") LocalDate date);

    /**
     * Date de la toute première séance <strong>chargée</strong> (RPE renseigné) d'un athlète.
     * Sert à savoir si son historique suffit à publier un ACWR : la fenêtre de calcul ne remonte
     * qu'à 28 jours et ne peut pas distinguer un athlète neuf d'un athlète installé.
     */
    @Query("""
            select min(w.scheduledDate) from Workout w
            where w.athlete.id = :athleteId and w.rpe is not null
            """)
    Optional<LocalDate> findFirstLoadedSessionDate(@Param("athleteId") UUID athleteId);

    // --- Suivi de plan (séances liées via plan_id) ---
    /**
     * Les séances d'un ensemble d'athlètes sur une plage de dates — le bilan de la semaine.
     *
     * <p>Une requête plutôt qu'une boucle par athlète : le bilan du coach couvre tout un club, et
     * une requête par athlète ferait vingt allers-retours pour un message hebdomadaire.</p>
     */
    List<Workout> findByAthleteIdInAndScheduledDateBetween(Collection<UUID> athleteIds,
                                                           LocalDate from, LocalDate to);

    long countByPlanIdAndAthleteId(UUID planId, UUID athleteId);

    long countByPlanIdAndAthleteIdAndStatus(UUID planId, UUID athleteId, WorkoutStatus status);

    /** Suppression propre : retire les séances d'un plan encore planifiées (préserve l'historique réalisé). */
    void deleteByPlanIdAndAthleteIdAndStatus(UUID planId, UUID athleteId, WorkoutStatus status);
}
