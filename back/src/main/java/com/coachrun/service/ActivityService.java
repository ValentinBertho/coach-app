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

    public List<ActivityResponse> list(UUID clubId, UUID athleteId) {
        return activityRepository.findByClubIdAndAthleteIdOrderByActivityDateDesc(clubId, athleteId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ActivityResponse importActivity(UUID clubId, UUID athleteId, ActivityImportRequest request) {
        Athlete athlete = athleteRepository.findByIdAndClubId(athleteId, clubId)
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
