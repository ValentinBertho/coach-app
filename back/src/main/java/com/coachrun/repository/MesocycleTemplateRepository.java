package com.coachrun.repository;

import com.coachrun.entity.MesocycleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MesocycleTemplateRepository extends JpaRepository<MesocycleTemplate, UUID> {

    List<MesocycleTemplate> findByClubIdOrderByNameAsc(UUID clubId);

    /** Scoping tenant systématique (anti-IDOR). */
    Optional<MesocycleTemplate> findByIdAndClubId(UUID id, UUID clubId);
}
