package com.coachrun.repository;

import com.coachrun.entity.Club;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Accès aux clubs. Les requêtes métier scopées par tenant viendront avec les features
 * (cf. Claude.md : toujours scoper par clubId, jamais de findById nu sur les ressources tenant).
 */
public interface ClubRepository extends JpaRepository<Club, UUID> {

    boolean existsBySlug(String slug);

    org.springframework.data.domain.Page<Club> findByNameContainingIgnoreCase(
            String name, org.springframework.data.domain.Pageable pageable);
}
