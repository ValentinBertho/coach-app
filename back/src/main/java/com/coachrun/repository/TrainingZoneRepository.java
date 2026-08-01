package com.coachrun.repository;

import com.coachrun.entity.TrainingZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingZoneRepository extends JpaRepository<TrainingZone, UUID> {

    List<TrainingZone> findByClubIdOrderBySortOrderAscNameAsc(UUID clubId);

    /** Zones d'un modèle de zones donné (l'échelle réellement appliquée à un athlète). */
    List<TrainingZone> findByClubIdAndZoneSetIdOrderBySortOrderAscNameAsc(UUID clubId, UUID zoneSetId);

    /** Zones pas encore rattachées à un modèle (héritage d'avant les jeux de zones). */
    List<TrainingZone> findByClubIdAndZoneSetIsNull(UUID clubId);

    Optional<TrainingZone> findByIdAndClubId(UUID id, UUID clubId);

    boolean existsByClubId(UUID clubId);
}
