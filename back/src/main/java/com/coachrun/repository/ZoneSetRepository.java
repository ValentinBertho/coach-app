package com.coachrun.repository;

import com.coachrun.entity.ZoneSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZoneSetRepository extends JpaRepository<ZoneSet, UUID> {

    List<ZoneSet> findByClubIdOrderBySortOrderAscNameAsc(UUID clubId);

    Optional<ZoneSet> findByIdAndClubId(UUID id, UUID clubId);

    Optional<ZoneSet> findFirstByClubIdAndDefaultSetTrue(UUID clubId);
}
