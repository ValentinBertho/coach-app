package com.coachrun.repository;

import com.coachrun.entity.AthleteUnavailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AthleteUnavailabilityRepository extends JpaRepository<AthleteUnavailability, UUID> {

    List<AthleteUnavailability> findByClubIdAndAthleteIdOrderByStartDateDesc(UUID clubId, UUID athleteId);

    /** Toutes les indisponibilités d'un athlète, tous clubs confondus (export RGPD). */
    List<AthleteUnavailability> findByAthleteIdOrderByStartDateDesc(UUID athleteId);

    Optional<AthleteUnavailability> findByIdAndClubId(UUID id, UUID clubId);

    List<AthleteUnavailability> findByAthleteIdAndEndDateGreaterThanEqualOrderByStartDateAsc(
            UUID athleteId, LocalDate from);

    /**
     * Indisponibilités chevauchant une fenêtre — sert à taire les alertes d'un athlète dont on
     * sait déjà qu'il ne s'entraîne pas.
     */
    List<AthleteUnavailability> findByAthleteIdAndEndDateGreaterThanEqualAndStartDateLessThanEqual(
            UUID athleteId, LocalDate windowStart, LocalDate windowEnd);
}
