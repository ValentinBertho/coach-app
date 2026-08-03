package com.coachrun.repository;

import com.coachrun.entity.BetaFeedback;
import com.coachrun.entity.enums.FeedbackStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BetaFeedbackRepository extends JpaRepository<BetaFeedback, UUID> {

    /** File de traitement, filtrable par statut. Paginée : elle grossit à chaque retour. */
    Page<BetaFeedback> findByStatusOrderByCreatedAtDesc(FeedbackStatus status, Pageable pageable);

    Page<BetaFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(FeedbackStatus status);
}
