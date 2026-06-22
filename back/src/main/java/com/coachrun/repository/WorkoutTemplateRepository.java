package com.coachrun.repository;

import com.coachrun.entity.WorkoutTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkoutTemplateRepository extends JpaRepository<WorkoutTemplate, UUID> {

    Page<WorkoutTemplate> findByClubId(UUID clubId, Pageable pageable);

    Page<WorkoutTemplate> findByClubIdAndNameContainingIgnoreCase(UUID clubId, String name, Pageable pageable);

    Optional<WorkoutTemplate> findByIdAndClubId(UUID id, UUID clubId);
}
