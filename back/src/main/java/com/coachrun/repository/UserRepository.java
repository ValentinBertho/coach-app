package com.coachrun.repository;

import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByAthleteId(UUID athleteId);

    Optional<User> findFirstByClubIdAndRole(UUID clubId, UserRole role);
}
