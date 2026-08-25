package com.coachrun.repository;

import com.coachrun.entity.AdminAuditLog;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditTarget;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    /**
     * Recherche filtrée du journal. Tous les critères sont facultatifs : le paramètre nul
     * neutralise sa clause, ce qui évite d'écrire une Specification pour cinq filtres.
     */
    @Query("""
            select a from AdminAuditLog a
            where (:action is null or a.action = :action)
              and (:targetType is null or a.targetType = :targetType)
              and (:actorUserId is null or a.actorUserId = :actorUserId)
              and (:targetId is null or a.targetId = :targetId)
              and (:since is null or a.occurredAt >= :since)
              and (:q = '' or lower(coalesce(a.targetLabel, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(a.actorEmail, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(a.summary, '')) like lower(concat('%', :q, '%')))
            order by a.occurredAt desc
            """)
    Page<AdminAuditLog> search(@Param("action") AdminAuditAction action,
                               @Param("targetType") AdminAuditTarget targetType,
                               @Param("actorUserId") UUID actorUserId,
                               @Param("targetId") UUID targetId,
                               @Param("since") Instant since,
                               @Param("q") String q,
                               Pageable pageable);

    /** Dernières lignes, tous filtres confondus : bandeau « dernières actions » du pilotage. */
    List<AdminAuditLog> findTop10ByOrderByOccurredAtDesc();

    /** Historique d'une ressource précise (fiche utilisateur, fiche club). */
    List<AdminAuditLog> findTop20ByTargetIdOrderByOccurredAtDesc(UUID targetId);

    long countByOccurredAtAfter(Instant since);
}
