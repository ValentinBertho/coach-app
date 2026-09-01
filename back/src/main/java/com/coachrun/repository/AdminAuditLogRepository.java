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
     *
     * <h2>Le {@code coalesce} sur {@code :since} n'est pas une coquetterie</h2>
     *
     * <p>Écrite {@code (:since is null or a.occurredAt >= :since)}, cette clause plaçait le
     * paramètre <b>seul</b> dans un {@code is null}, sans colonne en face. PostgreSQL n'a alors
     * rien pour en déduire le type et refuse la requête entière — « could not determine data type
     * of parameter $9 » (SQLSTATE 42P18) — donc 500 sur tout l'écran, filtres compris.</p>
     *
     * <p>Les quatre filtres au-dessus gardent cette forme sans problème : ce sont des énumérations
     * et des UUID, que le pilote JDBC transmet avec un type concret. Un {@code Instant}, lui, part
     * avec un type non spécifié — c'est ce qui fait la différence, et c'est pourquoi seul
     * celui-ci a dû changer.</p>
     *
     * <p>{@code coalesce(:since, a.occurredAt)} ancre le type sur la colonne et dit exactement la
     * même chose : sans borne, on compare la date à elle-même, ce qui est toujours vrai.
     * {@code MessageRepository} documente le même piège pour la messagerie, où il avait déjà
     * coûté un écran entier en production — H2, sur lequel tourne la suite, l'accepte sans
     * broncher (cf. {@code AdminAuditOnPostgresTest}).</p>
     */
    @Query("""
            select a from AdminAuditLog a
            where (:action is null or a.action = :action)
              and (:targetType is null or a.targetType = :targetType)
              and (:actorUserId is null or a.actorUserId = :actorUserId)
              and (:targetId is null or a.targetId = :targetId)
              and a.occurredAt >= coalesce(:since, a.occurredAt)
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
