package com.coachrun.repository;

import com.coachrun.entity.SessionCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionCategoryRepository extends JpaRepository<SessionCategory, UUID> {

    List<SessionCategory> findByClubIdOrderBySortOrderAscNameAsc(UUID clubId);

    Optional<SessionCategory> findByIdAndClubId(UUID id, UUID clubId);
}
