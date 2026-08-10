package com.coachrun.repository;

import com.coachrun.entity.ScheduledStrengthSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduledStrengthSessionRepository extends JpaRepository<ScheduledStrengthSession, UUID> {

    List<ScheduledStrengthSession> findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
            UUID clubId, UUID athleteId, LocalDate from, LocalDate to);

    Optional<ScheduledStrengthSession> findByIdAndClubId(UUID id, UUID clubId);

    Optional<ScheduledStrengthSession> findByIdAndClubIdAndAthleteId(UUID id, UUID clubId, UUID athleteId);

    // --- Portail athlète (scoping par athleteId du principal) ---
    List<ScheduledStrengthSession> findByAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
            UUID athleteId, LocalDate from, LocalDate to);

    Optional<ScheduledStrengthSession> findByIdAndAthleteId(UUID id, UUID athleteId);

    /** Toutes les séances de force d'un athlète — export et effacement RGPD (pas de fenêtre). */
    List<ScheduledStrengthSession> findByAthleteIdOrderByScheduledDateAsc(UUID athleteId);

    /** Séances de force d'un jour donné restées non clôturées (rappel de débriefing). */
    List<ScheduledStrengthSession> findByScheduledDateAndCompletedFalse(LocalDate date);

    /**
     * Séances de force d'un jour donné, athlète chargé — point de programme du soir.
     *
     * <p>Ce point de programme ne regardait que les séances course : un athlète dont le lendemain
     * ne portait que du renforcement s'entendait annoncer « Repos demain », alors que le rappel
     * lui-même est écrit pour couvrir « footing le matin et renforcement le soir ».</p>
     */
    @EntityGraph(attributePaths = "athlete")
    List<ScheduledStrengthSession> findByScheduledDate(LocalDate date);

    /**
     * Dernier retour de séance de force portant un signal de forme (fatigue ou douleur).
     * Sert à alimenter l'état de forme et les alertes douleur au même titre que les séances
     * course — la forme est fatigue + douleur, course et force confondues.
     */
    @Query("""
            select s from ScheduledStrengthSession s
            where s.athlete.id = :athleteId
              and (s.sessionFatigue is not null or s.sessionPain is not null)
            order by s.scheduledDate desc, s.createdAt desc
            """)
    List<ScheduledStrengthSession> findLatestFeedback(@Param("athleteId") UUID athleteId, Pageable pageable);

    /**
     * File « retours à traiter » : séances de force porteuses d'un retour athlète (RPE, douleur
     * ou commentaire) que le coach n'a pas encore marquées comme traitées.
     */
    @Query("""
            select s from ScheduledStrengthSession s
            where s.athlete.id in :athleteIds
              and s.coachReviewedAt is null
              and s.scheduledDate >= :since
              and (s.sessionRpe is not null or s.sessionPain is not null or s.sessionComment is not null)
            order by s.scheduledDate desc, s.createdAt desc
            """)
    List<ScheduledStrengthSession> findPendingFeedback(@Param("athleteIds") Collection<UUID> athleteIds,
                                                       @Param("since") LocalDate since);

    // --- Suivi de plan (séances de force liées via plan_id) ---
    long countByPlanIdAndAthleteId(UUID planId, UUID athleteId);

    long countByPlanIdAndAthleteIdAndCompletedTrue(UUID planId, UUID athleteId);

    /** Suppression propre : retire les séances de force d'un plan non encore réalisées. */
    void deleteByPlanIdAndAthleteIdAndCompletedFalse(UUID planId, UUID athleteId);
}
