package com.coachrun.repository;

import com.coachrun.entity.LactateTest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LactateTestRepository extends JpaRepository<LactateTest, UUID> {

    List<LactateTest> findByClubIdAndAthleteIdOrderByTestDateDesc(UUID clubId, UUID athleteId);

    /** Variante athlète-scopée (portail /me). */
    List<LactateTest> findByAthleteIdOrderByTestDateDesc(UUID athleteId);

    @EntityGraph(attributePaths = "steps")
    Optional<LactateTest> findByIdAndClubId(UUID id, UUID clubId);

    /** Détail athlète-scopé avec paliers (portail /me). */
    @EntityGraph(attributePaths = "steps")
    Optional<LactateTest> findByIdAndAthleteId(UUID id, UUID athleteId);
}
