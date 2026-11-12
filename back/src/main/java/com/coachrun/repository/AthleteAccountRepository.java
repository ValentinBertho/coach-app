package com.coachrun.repository;

import com.coachrun.entity.AthleteAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AthleteAccountRepository extends JpaRepository<AthleteAccount, UUID> {

    Optional<AthleteAccount> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
