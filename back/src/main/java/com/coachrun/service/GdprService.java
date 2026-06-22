package com.coachrun.service;

import com.coachrun.dto.response.ActivityResponse;
import com.coachrun.dto.response.AthleteExportResponse;
import com.coachrun.dto.response.AthleteResponse;
import com.coachrun.dto.response.WorkoutResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * RGPD : portabilité (export) et droit à l'oubli (suppression). La suppression de
 * l'athlète purge en cascade ses séances, activités et son compte (FK ON DELETE CASCADE).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GdprService {

    private final AthleteRepository athleteRepository;
    private final WorkoutRepository workoutRepository;
    private final ActivityRepository activityRepository;

    public AthleteExportResponse export(UUID athleteId) {
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        var workouts = workoutRepository.findByAthleteIdOrderByScheduledDateAsc(athleteId)
                .stream().map(WorkoutResponse::from).toList();
        var activities = activityRepository.findByAthleteIdOrderByActivityDateDesc(athleteId)
                .stream().map(ActivityResponse::from).toList();
        return new AthleteExportResponse(
                Instant.now(), athlete.getHealthDataConsentAt(),
                AthleteResponse.from(athlete), workouts, activities);
    }

    @Transactional
    public void deleteAthleteData(UUID athleteId) {
        if (!athleteRepository.existsById(athleteId)) {
            throw new NotFoundException("Athlète introuvable.");
        }
        athleteRepository.deleteById(athleteId);
        log.warn("[RGPD] Données de l'athlète {} supprimées (droit à l'oubli).", athleteId);
    }
}
