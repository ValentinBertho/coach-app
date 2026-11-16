package com.coachrun.repository;

import com.coachrun.entity.CoachTestimonial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachTestimonialRepository extends JpaRepository<CoachTestimonial, UUID> {

    Optional<CoachTestimonial> findByCoachIdAndAthleteAccountId(UUID coachId, UUID accountId);

    List<CoachTestimonial> findByCoachIdOrderByCreatedAtDesc(UUID coachId);

    List<CoachTestimonial> findTop6ByCoachIdAndPublishedTrueOrderByUpdatedAtDesc(UUID coachId);
}
