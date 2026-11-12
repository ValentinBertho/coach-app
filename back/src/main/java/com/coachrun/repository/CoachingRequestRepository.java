package com.coachrun.repository;

import com.coachrun.entity.CoachingRequest;
import com.coachrun.entity.enums.CoachingRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachingRequestRepository extends JpaRepository<CoachingRequest, UUID> {

    List<CoachingRequest> findByAthleteAccountIdOrderByCreatedAtDesc(UUID athleteAccountId);

    List<CoachingRequest> findByCoachIdOrderByCreatedAtDesc(UUID coachId);

    Optional<CoachingRequest> findByIdAndAthleteAccountId(UUID id, UUID athleteAccountId);

    Optional<CoachingRequest> findByIdAndCoachId(UUID id, UUID coachId);

    /**
     * Une demande en attente existe-t-elle déjà pour ce couple ?
     *
     * <p>C'est la garde contre l'insistance : sans elle, un athlète pressé remplit la file d'un
     * coach de trente lignes identiques, et cette file devient inutilisable — donc ignorée.</p>
     */
    boolean existsByAthleteAccountIdAndCoachIdAndStatus(
            UUID athleteAccountId, UUID coachId, CoachingRequestStatus status);

    /** Combien de demandes cet athlète a-t-il en attente ? Le plafond global anti-spam. */
    long countByAthleteAccountIdAndStatus(UUID athleteAccountId, CoachingRequestStatus status);

    /** Combien en a-t-il envoyées depuis telle date ? Le plafond glissant. */
    long countByAthleteAccountIdAndCreatedAtAfter(UUID athleteAccountId, Instant since);

    /** Les demandes en attente de ce coach : la pastille de sa file. */
    long countByCoachIdAndStatus(UUID coachId, CoachingRequestStatus status);

    /**
     * Les demandes <b>tranchées</b> d'un coach, pour mesurer sa réactivité.
     *
     * <p>Acceptées et refusées seulement : un retrait par l'athlète ou une expiration ne disent
     * rien du coach — la première n'est pas de son fait, la seconde est justement l'absence de
     * réponse qu'on mesure ailleurs.</p>
     */
    @Query("""
            select r from CoachingRequest r
             where r.coach.id = :coachId
               and r.decidedAt is not null
               and r.status in (com.coachrun.entity.enums.CoachingRequestStatus.ACCEPTED,
                                com.coachrun.entity.enums.CoachingRequestStatus.DECLINED)
            """)
    List<CoachingRequest> findDecidedByCoach(@Param("coachId") UUID coachId);

    /**
     * Le dénominateur du taux de réponse : les demandes closes <b>que le coach pouvait trancher</b>.
     *
     * <p>Les expirations comptent — c'est tout l'intérêt, un silence doit peser quelque part. Les
     * retraits, non : l'athlète a repris sa demande, et rien ne dit que le coach n'allait pas
     * répondre. Les compter aurait fait baisser le taux d'un coach pour un geste qui n'est pas le
     * sien, sur une vitrine publique.</p>
     */
    @Query("""
            select count(r) from CoachingRequest r
             where r.coach.id = :coachId
               and r.status in (com.coachrun.entity.enums.CoachingRequestStatus.ACCEPTED,
                                com.coachrun.entity.enums.CoachingRequestStatus.DECLINED,
                                com.coachrun.entity.enums.CoachingRequestStatus.EXPIRED)
            """)
    long countAnswerableByCoach(@Param("coachId") UUID coachId);

    /** Les coachs ayant reçu au moins une demande : le balayage nocturne n'a pas à tous les lire. */
    @Query("select distinct r.coach.id from CoachingRequest r")
    List<UUID> findCoachIdsWithRequests();

    /**
     * Périme les demandes que personne n'a tranchées dans le délai.
     *
     * <p>Une écriture en masse plutôt qu'un statut calculé à la lecture : l'état doit être le même
     * pour tout le monde, et une demande « périmée à l'affichage mais PENDING en base » finirait
     * par être acceptée depuis un écran qui n'avait pas rafraîchi.</p>
     */
    @Modifying
    @Query("""
            update CoachingRequest r
               set r.status = com.coachrun.entity.enums.CoachingRequestStatus.EXPIRED
             where r.status = com.coachrun.entity.enums.CoachingRequestStatus.PENDING
               and r.expiresAt < :now
            """)
    int expireOverdue(@Param("now") Instant now);
}
