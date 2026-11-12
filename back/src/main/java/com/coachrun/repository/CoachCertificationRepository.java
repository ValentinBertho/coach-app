package com.coachrun.repository;

import com.coachrun.entity.CoachCertification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachCertificationRepository extends JpaRepository<CoachCertification, UUID> {

    List<CoachCertification> findByProfileIdOrderByObtainedYearDesc(UUID profileId);

    Optional<CoachCertification> findByIdAndProfileId(UUID id, UUID profileId);
}
