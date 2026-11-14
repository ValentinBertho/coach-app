package com.coachrun.repository;

import com.coachrun.entity.WorkoutTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

import java.util.Optional;
import java.util.UUID;

public interface WorkoutTemplateRepository extends JpaRepository<WorkoutTemplate, UUID> {

    Page<WorkoutTemplate> findByClubId(UUID clubId, Pageable pageable);

    Page<WorkoutTemplate> findByClubIdAndNameContainingIgnoreCase(UUID clubId, String name, Pageable pageable);

    Optional<WorkoutTemplate> findByIdAndClubId(UUID id, UUID clubId);

    /**
     * Compte une utilisation du modèle sans le « modifier ».
     *
     * <p>Requête directe plutôt que setters sur l'entité : passer par l'entité déclenchait
     * {@code @PreUpdate}, si bien que poser une séance au calendrier changeait la date de
     * modification (et la version) du modèle, alors que son contenu n'avait pas bougé.</p>
     */
    @Modifying
    @Query("update WorkoutTemplate t set t.useCount = t.useCount + 1, t.lastUsedAt = :now where t.id = :id")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);
}
