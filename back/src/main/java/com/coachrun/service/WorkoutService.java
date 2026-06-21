package com.coachrun.service;

import com.coachrun.dto.request.WorkoutRequest;
import com.coachrun.dto.request.WorkoutStepRequest;
import com.coachrun.dto.response.WorkoutResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Workout;
import com.coachrun.entity.WorkoutStep;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Gestion des séances prescrites : calendrier, CRUD scopé par club (anti-IDOR),
 * étapes structurées et transitions d'état validées.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkoutService {

    private final WorkoutRepository workoutRepository;
    private final AthleteRepository athleteRepository;
    private final NotificationService notificationService;

    public List<WorkoutResponse> calendar(UUID clubId, UUID athleteId, LocalDate from, LocalDate to) {
        return workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(clubId, athleteId, from, to)
                .stream().map(WorkoutResponse::from).toList();
    }

    public WorkoutResponse get(UUID clubId, UUID workoutId) {
        return WorkoutResponse.from(require(clubId, workoutId));
    }

    @Transactional
    public WorkoutResponse create(UUID clubId, UUID athleteId, WorkoutRequest request) {
        Athlete athlete = athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));

        Workout workout = new Workout();
        workout.setClub(athlete.getClub());
        workout.setAthlete(athlete);
        workout.setStatus(WorkoutStatus.PLANNED);
        apply(workout, request);

        workout = workoutRepository.save(workout);
        log.info("Séance créée {} (athlète={})", workout.getId(), athleteId);
        notificationService.notifyWorkoutPlanned(workout);
        return WorkoutResponse.from(workout);
    }

    @Transactional
    public WorkoutResponse update(UUID clubId, UUID workoutId, WorkoutRequest request) {
        Workout workout = require(clubId, workoutId);
        apply(workout, request);
        return WorkoutResponse.from(workout);
    }

    @Transactional
    public WorkoutResponse updateStatus(UUID clubId, UUID workoutId, WorkoutStatus target) {
        Workout workout = require(clubId, workoutId);
        if (!workout.getStatus().canTransitionTo(target)) {
            throw new ConflictException(
                    "Transition de statut interdite : " + workout.getStatus() + " → " + target);
        }
        workout.setStatus(target);
        return WorkoutResponse.from(workout);
    }

    @Transactional
    public void delete(UUID clubId, UUID workoutId) {
        Workout workout = require(clubId, workoutId);
        workoutRepository.delete(workout);
    }

    // ----- Portail athlète (scoping par athleteId du principal) -----

    public List<WorkoutResponse> todayForAthlete(UUID athleteId, LocalDate date) {
        return workoutRepository.findByAthleteIdAndScheduledDateOrderByCreatedAtAsc(athleteId, date)
                .stream().map(WorkoutResponse::from).toList();
    }

    public List<WorkoutResponse> athleteCalendar(UUID athleteId, LocalDate from, LocalDate to) {
        return workoutRepository
                .findByAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(athleteId, from, to)
                .stream().map(WorkoutResponse::from).toList();
    }

    @Transactional
    public WorkoutResponse submitFeedback(UUID athleteId, UUID workoutId,
                                          WorkoutStatus status, Integer rpe, String comment) {
        Workout workout = workoutRepository.findByIdAndAthleteId(workoutId, athleteId)
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
        if (status != null) {
            if (!workout.getStatus().canTransitionTo(status)) {
                throw new ConflictException(
                        "Transition de statut interdite : " + workout.getStatus() + " → " + status);
            }
            workout.setStatus(status);
        }
        workout.setRpe(rpe);
        workout.setAthleteComment(comment);
        notificationService.notifyAthleteFeedback(workout);
        return WorkoutResponse.from(workout);
    }

    private Workout require(UUID clubId, UUID workoutId) {
        return workoutRepository.findByIdAndClubId(workoutId, clubId)
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
    }

    private void apply(Workout workout, WorkoutRequest request) {
        workout.setScheduledDate(request.scheduledDate());
        workout.setType(request.type());
        workout.setTitle(request.title());
        workout.setNotes(request.notes());
        workout.setTargetDistanceM(request.targetDistanceM());
        workout.setTargetDurationS(request.targetDurationS());
        workout.replaceSteps(request.steps().stream().map(this::toStep).toList());
    }

    private WorkoutStep toStep(WorkoutStepRequest req) {
        WorkoutStep step = new WorkoutStep();
        step.setStepType(req.stepType());
        step.setRepetitions(Math.max(1, req.repetitions()));
        step.setZone(req.zone());
        step.setDistanceM(req.distanceM());
        step.setDurationS(req.durationS());
        step.setNotes(req.notes());
        return step;
    }
}
