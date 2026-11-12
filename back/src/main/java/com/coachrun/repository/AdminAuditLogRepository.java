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
import java.util.Collection;
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
     * <h2>La famille d'actions passe par une liste toujours pleine</h2>
     *
     * <p>{@code :actions} porte le filtre de portée (administration, sécurité, données
     * personnelles, coaching). Sans portée demandée, l'appelant passe <b>toutes</b> les valeurs de
     * l'énumération plutôt que {@code null} : un paramètre seul dans un {@code is null} est
     * exactement la forme que PostgreSQL refuse, et qui est documentée deux paragraphes plus bas.
     * Quelques dizaines de constantes dans un {@code in} ne coûtent rien ; un écran qui tombe en
     * 500, si.</p>
     *
     * <h2>Chercher un administrateur le trouve aussi derrière un emprunt</h2>
     *
     * <p>La recherche libre couvre {@code impersonatorEmail} en plus de {@code actorEmail} : sans
     * cela, taper l'adresse d'un administrateur ne rendait que les gestes faits sous sa propre
     * identité, et manquait exactement ceux qu'on cherche — ceux qu'il a faits au nom de
     * quelqu'un d'autre.</p>
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
              and a.action in :actions
              and a.occurredAt >= coalesce(:since, a.occurredAt)
              and (:q = '' or lower(coalesce(a.targetLabel, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(a.actorEmail, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(a.impersonatorEmail, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(a.summary, '')) like lower(concat('%', :q, '%')))
            order by a.occurredAt desc
            """)
    Page<AdminAuditLog> search(@Param("action") AdminAuditAction action,
                               @Param("actions") Collection<AdminAuditAction> actions,
                               @Param("targetType") AdminAuditTarget targetType,
                               @Param("actorUserId") UUID actorUserId,
                               @Param("targetId") UUID targetId,
                               @Param("since") Instant since,
                               @Param("q") String q,
                               Pageable pageable);

    /**
     * Dernières lignes d'une famille d'actions : bandeau « dernières actions » du pilotage.
     *
     * <p>Remplace un {@code findTop10ByOrderByOccurredAtDesc} qui ne filtrait rien. Depuis que le
     * journal consigne aussi les connexions, dix lignes sans filtre sont dix connexions — le
     * bandeau du tableau de bord, lui, existe pour montrer les gestes d'administration.</p>
     */
    List<AdminAuditLog> findTop10ByActionInOrderByOccurredAtDesc(Collection<AdminAuditAction> actions);

    /** Historique d'une ressource précise (fiche utilisateur, fiche club). */
    List<AdminAuditLog> findTop20ByTargetIdOrderByOccurredAtDesc(UUID targetId);

    long countByActionInAndOccurredAtAfter(Collection<AdminAuditAction> actions, Instant since);

    /**
     * Purge de conservation, bornée à une famille d'actions.
     *
     * <p>Sert au seul nettoyage des traces d'accès (cf. {@code AuditLogPurgeScheduler}) : ce sont
     * des données de connexion, que la CNIL recommande de ne pas conserver indéfiniment, et les
     * seules dont le volume l'exige. Les gestes d'administration, eux, restent — ils sont rares et
     * ce sont eux qu'on vient chercher des années plus tard.</p>
     */
    long deleteByActionInAndOccurredAtBefore(Collection<AdminAuditAction> actions, Instant cutoff);
}
