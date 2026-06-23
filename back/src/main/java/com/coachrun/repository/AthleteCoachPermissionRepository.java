package com.coachrun.repository;

import com.coachrun.entity.AthleteCoachPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AthleteCoachPermissionRepository extends JpaRepository<AthleteCoachPermission, UUID> {

    Optional<AthleteCoachPermission> findByAthleteIdAndCoachId(UUID athleteId, UUID coachId);

    List<AthleteCoachPermission> findByAthleteId(UUID athleteId);

    List<AthleteCoachPermission> findByCoachId(UUID coachId);
}
