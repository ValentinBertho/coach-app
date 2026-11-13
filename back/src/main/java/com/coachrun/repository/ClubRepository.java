package com.coachrun.repository;

import com.coachrun.entity.Club;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import java.util.UUID;

/**
 * Accès aux clubs. Les requêtes métier scopées par tenant viendront avec les features
 * (cf. Claude.md : toujours scoper par clubId, jamais de findById nu sur les ressources tenant).
 */
public interface ClubRepository extends JpaRepository<Club, UUID> {

    boolean existsBySlug(String slug);

    org.springframework.data.domain.Page<Club> findByNameContainingIgnoreCase(
            String name, org.springframework.data.domain.Pageable pageable);

    /** Recherche libre bornée (nom ou slug), pour la recherche globale du back-office. */
    @org.springframework.data.jpa.repository.Query("""
            select c from Club c
            where lower(c.name) like lower(concat('%', :q, '%'))
               or lower(c.slug) like lower(concat('%', :q, '%'))
            order by c.name
            """)
    java.util.List<Club> quickSearch(@org.springframework.data.repository.query.Param("q") String q,
                                     org.springframework.data.domain.Pageable pageable);

    long countByStatus(com.coachrun.entity.enums.ClubStatus status);

    long countByCreatedAtAfter(java.time.Instant since);

    /** Recherche filtrée du back-office : nom ou slug, et statut facultatif. Sans les essais. */
    @org.springframework.data.jpa.repository.Query("""
            select c from Club c
            where c.trialExpiresAt is null
              and (:status is null or c.status = :status)
              and (:q = '' or lower(c.name) like lower(concat('%', :q, '%'))
                   or lower(c.slug) like lower(concat('%', :q, '%')))
            """)
    org.springframework.data.domain.Page<Club> searchAdmin(
            @org.springframework.data.repository.query.Param("status")
            com.coachrun.entity.enums.ClubStatus status,
            @org.springframework.data.repository.query.Param("q") String q,
            org.springframework.data.domain.Pageable pageable);

    /** Les vrais clubs, sans les espaces d'essai (compteurs de l'administration). */
    long countByTrialExpiresAtIsNull();

    /** Espaces d'essai encore ouverts (plafond de {@code TrialService}). */
    long countByTrialExpiresAtAfter(java.time.Instant now);

    /** Espaces d'essai arrivés à échéance, à supprimer. */
    @Query("select c.id from Club c where c.trialExpiresAt is not null and c.trialExpiresAt < :now")
    java.util.List<UUID> findIdsByTrialExpiresAtBefore(@Param("now") java.time.Instant now);

    /**
     * Suppression par requête : la base emporte les lignes filles (ON DELETE CASCADE).
     * Un {@code delete(entity)} échoue sous Hibernate 6.6 dès qu'une entité chargée dans la même
     * transaction pointe encore vers la ligne supprimée (TransientObjectException). Les
     * modifications en attente sont écrites avant, et la session est vidée après.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Club e where e.id = :id")
    int hardDelete(@Param("id") UUID id);
}
