package com.coachrun.repository;

import com.coachrun.entity.RaceObjective;
import com.coachrun.entity.enums.RaceObjectiveStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceObjectiveRepository extends JpaRepository<RaceObjective, UUID> {

    List<RaceObjective> findByClubIdAndAthleteIdOrderByRaceDateAsc(UUID clubId, UUID athleteId);

    /** Tous les objectifs d'un athlète, tous clubs confondus (export RGPD). */
    List<RaceObjective> findByAthleteIdOrderByRaceDateAsc(UUID athleteId);

    Optional<RaceObjective> findByIdAndClubId(UUID id, UUID clubId);

    Optional<RaceObjective> findFirstByAthleteIdAndStatusAndRaceDateGreaterThanEqualOrderByRaceDateAsc(
            UUID athleteId, RaceObjectiveStatus status, LocalDate from);

    List<RaceObjective> findTop5ByClubIdAndStatusAndRaceDateGreaterThanEqualOrderByRaceDateAsc(
            UUID clubId, RaceObjectiveStatus status, LocalDate from);

    /** Prochaines courses restreintes à un périmètre d'athlètes (cockpit coach). */
    List<RaceObjective> findTop5ByAthleteIdInAndStatusAndRaceDateGreaterThanEqualOrderByRaceDateAsc(
            Collection<UUID> athleteIds, RaceObjectiveStatus status, LocalDate from);

    /** Courses tombant un jour donné, tous clubs confondus (rappels J-7 et J-1). */
    List<RaceObjective> findByStatusAndRaceDate(RaceObjectiveStatus status, LocalDate raceDate);

    /** Les courses d'un ensemble d'athlètes dans une fenêtre — « 3 courses dans les 15 jours ». */
    List<RaceObjective> findByAthleteIdInAndRaceDateBetween(java.util.Collection<UUID> athleteIds,
                                                            LocalDate from, LocalDate to);
}
