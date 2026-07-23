package com.coachrun.repository;

import com.coachrun.entity.MetricType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetricTypeRepository extends JpaRepository<MetricType, UUID> {

    /** Catalogue visible d'un club : métriques globales (club_id NULL) + métriques custom du club. */
    @Query("""
            SELECT m FROM MetricType m
            WHERE m.club IS NULL OR m.club.id = :clubId
            ORDER BY m.sortOrder ASC, m.name ASC
            """)
    List<MetricType> findVisibleForClub(@Param("clubId") UUID clubId);

    /** Métrique accessible au club : soit globale, soit lui appartenant. */
    @Query("""
            SELECT m FROM MetricType m
            WHERE m.id = :id AND (m.club IS NULL OR m.club.id = :clubId)
            """)
    Optional<MetricType> findVisibleForClub(@Param("id") UUID id, @Param("clubId") UUID clubId);
}
