package com.coachrun.service;

import com.coachrun.dto.request.UnavailabilityRequest;
import com.coachrun.dto.response.UnavailabilityResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteUnavailability;
import com.coachrun.exception.ApiException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.AthleteUnavailabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Indisponibilités d'un athlète (CRUD scopé club) + lecture côté athlète. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnavailabilityService {

    private final AthleteUnavailabilityRepository repository;
    private final AthleteRepository athleteRepository;

    public List<UnavailabilityResponse> list(UUID clubId, UUID athleteId) {
        return repository.findByClubIdAndAthleteIdOrderByStartDateDesc(clubId, athleteId)
                .stream().map(UnavailabilityResponse::from).toList();
    }

    /** Indisponibilités en cours ou à venir (portail athlète). */
    public List<UnavailabilityResponse> current(UUID athleteId) {
        return repository.findByAthleteIdAndEndDateGreaterThanEqualOrderByStartDateAsc(athleteId, LocalDate.now())
                .stream().map(UnavailabilityResponse::from).toList();
    }

    @Transactional
    public UnavailabilityResponse create(UUID clubId, UUID athleteId, UnavailabilityRequest req) {
        Athlete athlete = athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        AthleteUnavailability u = new AthleteUnavailability();
        u.setClub(athlete.getClub());
        u.setAthlete(athlete);
        apply(u, req);
        return UnavailabilityResponse.from(repository.save(u));
    }

    @Transactional
    public UnavailabilityResponse update(UUID clubId, UUID id, UnavailabilityRequest req) {
        AthleteUnavailability u = require(clubId, id);
        apply(u, req);
        return UnavailabilityResponse.from(u);
    }

    @Transactional
    public void delete(UUID clubId, UUID id) {
        repository.delete(require(clubId, id));
    }

    private AthleteUnavailability require(UUID clubId, UUID id) {
        return repository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Indisponibilité introuvable."));
    }

    private void apply(AthleteUnavailability u, UnavailabilityRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La date de fin précède la date de début.");
        }
        u.setStartDate(req.startDate());
        u.setEndDate(req.endDate());
        u.setReason(req.reason());
        u.setNotes(req.notes());
    }
}
