package com.coachrun.service;

import com.coachrun.dto.request.RaceObjectiveRequest;
import com.coachrun.dto.response.RaceObjectiveResponse;
import com.coachrun.entity.RaceObjective;
import com.coachrun.entity.enums.RaceObjectiveStatus;
import com.coachrun.entity.enums.RacePriority;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.RaceObjectiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Courses cibles d'un athlète (CRUD scopé club) + prochaine course (portail athlète). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RaceObjectiveService {

    private final RaceObjectiveRepository raceRepository;
    private final AthleteRepository athleteRepository;

    /** Liste complète de mes objectifs — variante athlète-scopée (portail /me). */
    public List<RaceObjectiveResponse> listForAthlete(UUID athleteId) {
        com.coachrun.entity.Athlete a = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return list(a.getClub().getId(), athleteId);
    }

    public List<RaceObjectiveResponse> list(UUID clubId, UUID athleteId) {
        return raceRepository.findByClubIdAndAthleteIdOrderByRaceDateAsc(clubId, athleteId)
                .stream().map(RaceObjectiveResponse::from).toList();
    }

    @Transactional
    public RaceObjectiveResponse create(UUID clubId, UUID athleteId, RaceObjectiveRequest request) {
        var athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        RaceObjective race = new RaceObjective();
        race.setClub(athlete.getClub());
        race.setAthlete(athlete);
        apply(race, request);
        return RaceObjectiveResponse.from(raceRepository.save(race));
    }

    @Transactional
    public RaceObjectiveResponse update(UUID clubId, UUID raceId, RaceObjectiveRequest request) {
        RaceObjective race = require(clubId, raceId);
        apply(race, request);
        return RaceObjectiveResponse.from(race);
    }

    @Transactional
    public void delete(UUID clubId, UUID raceId) {
        raceRepository.delete(require(clubId, raceId));
    }

    // --- Portail athlète : l'athlète gère ses propres objectifs (CDC §10) -----

    @Transactional
    public RaceObjectiveResponse createForAthlete(UUID athleteId, RaceObjectiveRequest request) {
        com.coachrun.entity.Athlete a = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return create(a.getClub().getId(), athleteId, request);
    }

    @Transactional
    public RaceObjectiveResponse updateForAthlete(UUID athleteId, UUID raceId, RaceObjectiveRequest request) {
        RaceObjective race = requireForAthlete(athleteId, raceId);
        apply(race, request);
        return RaceObjectiveResponse.from(race);
    }

    @Transactional
    public void deleteForAthlete(UUID athleteId, UUID raceId) {
        raceRepository.delete(requireForAthlete(athleteId, raceId));
    }

    /** Garde-fou anti-IDOR : l'objectif doit appartenir à l'athlète du principal. */
    private RaceObjective requireForAthlete(UUID athleteId, UUID raceId) {
        return raceRepository.findById(raceId)
                .filter(r -> r.getAthlete().getId().equals(athleteId))
                .orElseThrow(() -> new NotFoundException("Course introuvable."));
    }

    public Optional<RaceObjectiveResponse> nextRace(UUID athleteId) {
        return raceRepository
                .findFirstByAthleteIdAndStatusAndRaceDateGreaterThanEqualOrderByRaceDateAsc(
                        athleteId, RaceObjectiveStatus.UPCOMING, LocalDate.now())
                .map(RaceObjectiveResponse::from);
    }

    private RaceObjective require(UUID clubId, UUID raceId) {
        return raceRepository.findByIdAndClubId(raceId, clubId)
                .orElseThrow(() -> new NotFoundException("Course introuvable."));
    }

    private void apply(RaceObjective race, RaceObjectiveRequest request) {
        race.setName(request.name());
        race.setRaceDate(request.raceDate());
        race.setDistanceM(request.distanceM());
        race.setTargetTimeS(request.targetTimeS());
        race.setPriority(request.priority() != null ? request.priority() : RacePriority.B);
        race.setStatus(request.status() != null ? request.status() : RaceObjectiveStatus.UPCOMING);
    }
}
