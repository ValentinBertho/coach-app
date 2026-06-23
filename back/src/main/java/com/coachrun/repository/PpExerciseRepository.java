package com.coachrun.repository;

import com.coachrun.entity.PpExercise;
import com.coachrun.entity.enums.EquipmentType;
import com.coachrun.entity.enums.ExerciseCategory;
import com.coachrun.entity.enums.ExerciseLevel;
import com.coachrun.entity.enums.MuscleGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PpExerciseRepository extends JpaRepository<PpExercise, UUID> {

    Optional<PpExercise> findByIdAndClubId(UUID id, UUID clubId);

    /**
     * Recherche filtrable (catégorie, niveau, groupe musculaire, matériel, texte). Les enums
     * nuls désactivent le filtre ; {@code q} est toujours lié à "" (jamais null) — cf. RAF (piège
     * du paramètre null typé bytea sur PostgreSQL).
     */
    @Query("""
            select distinct e from PpExercise e
            where e.club.id = :clubId
              and e.archived = false
              and (:category is null or e.category = :category)
              and (:level is null or e.level = :level)
              and (:muscle is null or :muscle member of e.muscleGroups)
              and (:equipment is null or :equipment member of e.equipment)
              and lower(e.name) like lower(concat('%', :q, '%'))
            order by e.name asc
            """)
    Page<PpExercise> search(@Param("clubId") UUID clubId,
                            @Param("category") ExerciseCategory category,
                            @Param("level") ExerciseLevel level,
                            @Param("muscle") MuscleGroup muscle,
                            @Param("equipment") EquipmentType equipment,
                            @Param("q") String q,
                            Pageable pageable);
}
