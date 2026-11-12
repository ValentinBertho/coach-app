package com.coachrun.repository;

import com.coachrun.entity.CoachOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachOfferRepository extends JpaRepository<CoachOffer, UUID> {

    List<CoachOffer> findByProfileIdOrderByPositionAscCreatedAtAsc(UUID profileId);

    List<CoachOffer> findByProfileIdAndActiveTrueOrderByPositionAscCreatedAtAsc(UUID profileId);

    Optional<CoachOffer> findByIdAndProfileId(UUID id, UUID profileId);

    boolean existsByProfileIdAndActiveTrue(UUID profileId);
}
