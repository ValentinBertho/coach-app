package com.coachrun.repository;

import com.coachrun.entity.CoachProfile;
import com.coachrun.entity.enums.CoachProfileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CoachProfileRepository extends JpaRepository<CoachProfile, UUID> {

    Optional<CoachProfile> findByCoachId(UUID coachId);

    boolean existsBySlug(String slug);

    /** La fiche telle qu'on l'atteint depuis une adresse partagée. */
    Optional<CoachProfile> findBySlug(String slug);

    /** File d'arbitrage du back-office : sans filtre, tout l'historique. */
    Page<CoachProfile> findByStatusOrderBySubmittedAtAsc(CoachProfileStatus status, Pageable pageable);

    Page<CoachProfile> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    long countByStatus(CoachProfileStatus status);

    /** Les fiches visibles du public, pour l'annuaire (lot suivant). */
    Page<CoachProfile> findByStatusIn(Collection<CoachProfileStatus> statuses, Pageable pageable);
}
