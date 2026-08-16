package com.coachrun.repository;

import com.coachrun.entity.AthleteProposal;
import com.coachrun.entity.enums.ProposalStatus;
import com.coachrun.entity.enums.ProposalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AthleteProposalRepository extends JpaRepository<AthleteProposal, UUID> {

    List<AthleteProposal> findByAthleteIdAndStatusOrderByCreatedAtDesc(UUID athleteId, ProposalStatus status);

    Optional<AthleteProposal> findByIdAndClubId(UUID id, UUID clubId);

    /** Propositions en attente d'un lot d'athlètes — la file du coach, en une requête. */
    @Query("""
            select p from AthleteProposal p
            where p.athlete.id in :athleteIds and p.status = :status
            order by p.requestedByAthlete desc, p.createdAt desc
            """)
    List<AthleteProposal> findPending(@Param("athleteIds") List<UUID> athleteIds,
                                      @Param("status") ProposalStatus status);

    /**
     * La même cause a-t-elle déjà produit une proposition&nbsp;? Toutes décisions confondues :
     * une suggestion refusée ne doit pas revenir, sinon le refus ne veut rien dire.
     */
    boolean existsByAthleteIdAndDedupeKey(UUID athleteId, String dedupeKey);

    List<AthleteProposal> findByAthleteIdAndTypeAndStatus(UUID athleteId, ProposalType type, ProposalStatus status);

    /** Propositions en attente dont le jour est passé : à périmer, pas à refuser. */
    @Query("""
            select p from AthleteProposal p
            where p.status = com.coachrun.entity.enums.ProposalStatus.PENDING
              and p.targetDate is not null and p.targetDate < :today
            """)
    List<AthleteProposal> findStale(@Param("today") LocalDate today);

    /** Propositions en attente visant une séance donnée (elle est supprimée, elles n'ont plus d'objet). */
    List<AthleteProposal> findByTargetIdAndStatus(UUID targetId, ProposalStatus status);
}
