package com.coachrun.service;

import com.coachrun.dto.request.ActivityImportRequest;
import com.coachrun.dto.response.ActivityResponse;
import com.coachrun.entity.Activity;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.ActivityStatus;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Import des activités réalisées (manuel pour l'instant), déduplication et rapprochement
 * automatique prévu/réalisé. Scopé par club (anti-IDOR).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final AthleteRepository athleteRepository;
    private final WorkoutRepository workoutRepository;
    private final MatchingService matchingService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public List<ActivityResponse> list(UUID clubId, UUID athleteId) {
        return activityRepository.findByClubIdAndAthleteIdOrderByActivityDateDesc(clubId, athleteId)
                .stream().map(this::toResponse).toList();
    }

    /** Liste — variante athlète-scopée (portail /me) : ne renvoie que mes activités. */
    public List<ActivityResponse> listForAthlete(UUID athleteId) {
        return activityRepository.findByAthleteIdOrderByActivityDateDesc(athleteId)
                .stream().map(this::toResponse).toList();
    }

    /** Tracé GPS — variante athlète-scopée : l'activité doit appartenir à l'athlète. */
    public java.util.List<double[]> routeForAthlete(UUID athleteId, UUID activityId) {
        Activity a = activityRepository.findById(activityId)
                .filter(act -> act.getAthlete().getId().equals(athleteId))
                .orElseThrow(() -> new NotFoundException("Activité introuvable."));
        if (a.getRouteJson() == null) {
            return java.util.List.of();
        }
        try {
            return objectMapper.readValue(a.getRouteJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<double[]>>() { });
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    @Transactional
    public ActivityResponse importActivity(UUID clubId, UUID athleteId, ActivityImportRequest request) {
        Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));

        ActivitySource source = request.sourceOrDefault();
        if (request.externalId() != null
                && activityRepository.existsByAthleteIdAndSourceAndExternalId(athleteId, source, request.externalId())) {
            throw new ConflictException("Cette activité a déjà été importée.");
        }

        Activity activity = new Activity();
        activity.setClub(athlete.getClub());
        activity.setAthlete(athlete);
        activity.setSource(source);
        activity.setExternalId(request.externalId());
        activity.setActivityDate(request.activityDate());
        activity.setTitle(request.title());
        activity.setDistanceM(request.distanceM());
        activity.setDurationS(request.durationS());
        activity.setAvgHr(request.avgHr());
        activity.setElevationGainM(request.elevationGainM());
        activity.setStatus(ActivityStatus.IMPORTED);

        autoMatch(athleteId, activity);
        activity = activityRepository.save(activity);
        log.info("Activité importée {} (athlète={}, statut={})", activity.getId(), athleteId, activity.getStatus());
        return toResponse(activity);
    }

    /** Import d'un fichier GPX/TCX → activité + tracé, puis rapprochement automatique. */
    @Transactional
    public ActivityResponse importFile(UUID clubId, UUID athleteId, String filename, byte[] bytes) {
        com.coachrun.entity.Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return createFromFile(athlete, athleteId, filename, bytes);
    }

    // --- Portail athlète : saisie / import par l'athlète sur ses propres données ---

    /** L'athlète enregistre une sortie libre (source toujours MANUAL). Scopé par son propre id. */
    @Transactional
    public ActivityResponse logForAthlete(UUID athleteId, ActivityImportRequest request) {
        com.coachrun.entity.Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        Activity activity = new Activity();
        activity.setClub(athlete.getClub());
        activity.setAthlete(athlete);
        activity.setSource(ActivitySource.MANUAL);
        activity.setActivityDate(request.activityDate());
        activity.setTitle(request.title());
        activity.setDistanceM(request.distanceM());
        activity.setDurationS(request.durationS());
        activity.setAvgHr(request.avgHr());
        activity.setElevationGainM(request.elevationGainM());
        activity.setStatus(ActivityStatus.IMPORTED);
        autoMatch(athleteId, activity);
        activity = activityRepository.save(activity);
        log.info("Sortie libre enregistrée {} (athlète={})", activity.getId(), athleteId);
        return toResponse(activity);
    }

    /** L'athlète importe sa propre trace (GPX/TCX). Scopé par son propre id. */
    @Transactional
    public ActivityResponse importFileForAthlete(UUID athleteId, String filename, byte[] bytes) {
        com.coachrun.entity.Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return createFromFile(athlete, athleteId, filename, bytes);
    }

    private ActivityResponse createFromFile(com.coachrun.entity.Athlete athlete, UUID athleteId,
                                            String filename, byte[] bytes) {
        com.coachrun.util.GpxParser.ParsedActivity parsed;
        try {
            parsed = com.coachrun.util.GpxParser.parse(bytes);
        } catch (IllegalArgumentException ex) {
            throw new com.coachrun.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    ex.getMessage());
        }

        Activity activity = new Activity();
        activity.setClub(athlete.getClub());
        activity.setAthlete(athlete);
        activity.setSource(ActivitySource.FILE);
        activity.setActivityDate(parsed.date());
        activity.setTitle(filename != null ? filename.replaceAll("\\.(gpx|tcx)$", "") : "Activité importée");
        activity.setDistanceM(parsed.distanceM());
        activity.setDurationS(parsed.durationS());
        activity.setElevationGainM(parsed.elevationGainM());
        activity.setStatus(ActivityStatus.IMPORTED);
        try {
            activity.setRouteJson(objectMapper.writeValueAsString(parsed.route()));
        } catch (Exception ignored) {
            // tracé optionnel
        }
        autoMatch(athleteId, activity);
        activity = activityRepository.save(activity);
        log.info("Activité importée par fichier {} ({} pts)", activity.getId(), parsed.route().size());
        return toResponse(activity);
    }

    /** Détail incluant le tracé GPS décodé. */
    public java.util.List<double[]> route(UUID clubId, UUID activityId) {
        Activity a = require(clubId, activityId);
        if (a.getRouteJson() == null) {
            return java.util.List.of();
        }
        try {
            return objectMapper.readValue(a.getRouteJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<double[]>>() { });
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    @Transactional
    public ActivityResponse matchManually(UUID clubId, UUID activityId, UUID workoutId) {
        Activity activity = require(clubId, activityId);
        Workout workout = workoutRepository.findByIdAndClubId(workoutId, clubId)
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
        link(activity, workout);
        return toResponse(activity);
    }

    @Transactional
    public ActivityResponse unmatch(UUID clubId, UUID activityId) {
        Activity activity = require(clubId, activityId);
        if (activity.getMatchedWorkoutId() != null) {
            workoutRepository.findByIdAndClubId(activity.getMatchedWorkoutId(), clubId)
                    .ifPresent(w -> w.setStatus(WorkoutStatus.PLANNED));
        }
        activity.setMatchedWorkoutId(null);
        activity.setStatus(ActivityStatus.UNMATCHED);
        return toResponse(activity);
    }

    private void autoMatch(UUID athleteId, Activity activity) {
        List<Workout> candidates = workoutRepository
                .findByAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        athleteId, activity.getActivityDate().minusDays(1), activity.getActivityDate().plusDays(1));
        Optional<Workout> best = matchingService.findBestMatch(activity, candidates);
        if (best.isPresent()) {
            link(activity, best.get());
        } else {
            activity.setStatus(ActivityStatus.UNMATCHED);
        }
    }

    private void link(Activity activity, Workout workout) {
        activity.setMatchedWorkoutId(workout.getId());
        activity.setStatus(ActivityStatus.MATCHED);
        workout.setStatus(matchingService.resolvedStatus(activity, workout));
    }

    private Activity require(UUID clubId, UUID activityId) {
        return activityRepository.findByIdAndClubId(activityId, clubId)
                .orElseThrow(() -> new NotFoundException("Activité introuvable."));
    }

    private ActivityResponse toResponse(Activity activity) {
        if (activity.getMatchedWorkoutId() == null) {
            return ActivityResponse.from(activity);
        }
        return workoutRepository.findById(activity.getMatchedWorkoutId())
                .map(w -> ActivityResponse.from(activity,
                        delta(activity.getDistanceM(), w.getTargetDistanceM()),
                        delta(activity.getDurationS(), w.getTargetDurationS())))
                .orElseGet(() -> ActivityResponse.from(activity));
    }

    private Integer delta(Integer actual, Integer target) {
        return (actual == null || target == null) ? null : actual - target;
    }
}
