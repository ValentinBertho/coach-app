package com.coachrun.service;

import com.coachrun.dto.request.WorkoutRequest;
import com.coachrun.dto.request.WorkoutStepRequest;
import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.dto.response.WorkoutPrescriptionResponse;
import com.coachrun.dto.response.WorkoutResponse;
import com.coachrun.dto.session.PrescribedWorkout;
import com.coachrun.dto.session.SessionStructure;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Workout;
import com.coachrun.entity.WorkoutStep;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.WorkoutRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    private final ObjectMapper objectMapper;

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

    /** Replanification (glisser-déposer) : change uniquement la date. */
    @Transactional
    public WorkoutResponse reschedule(UUID clubId, UUID workoutId, java.time.LocalDate date) {
        Workout workout = require(clubId, workoutId);
        workout.setScheduledDate(date);
        return WorkoutResponse.from(workout);
    }

    /**
     * Duplique une semaine de séances course vers une autre semaine (planification en cycles) :
     * recopie chaque séance en conservant son décalage de jour, le contenu et la prescription figée,
     * en statut {@code PLANNED} et sans retour. Ne notifie pas (édition en cours côté coach).
     */
    @Transactional
    public int duplicateWeek(UUID clubId, UUID athleteId, LocalDate sourceWeekStart, LocalDate targetWeekStart) {
        athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        if (sourceWeekStart.equals(targetWeekStart)) {
            throw new ConflictException("La semaine cible doit être différente de la semaine source.");
        }
        int created = copyWeek(clubId, athleteId, sourceWeekStart, targetWeekStart, 1.0);
        log.info("Semaine dupliquée athlète={} : {} séance(s) ({} → {})",
                athleteId, created, sourceWeekStart, targetWeekStart);
        return created;
    }

    /**
     * Périodisation assistée : génère un mésocycle progressif à partir d'une semaine type. Chaque
     * semaine recopie la semaine source en mettant à l'échelle distance/durée par un facteur de
     * progression ({@code +increasePct} par semaine d'accumulation, semaine de décharge toutes les
     * {@code deloadEvery} semaines à {@code deloadPct}). Statut PLANNED, sans retour, sans notif.
     */
    @Transactional
    public int generateMesocycle(UUID clubId, UUID athleteId, LocalDate sourceWeekStart,
                                 LocalDate firstWeekStart, int weeks, double increasePct,
                                 int deloadEvery, double deloadPct) {
        athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        int n = Math.max(1, Math.min(weeks, 16));
        int blockLen = Math.max(2, deloadEvery);
        int created = 0;
        int buildIndex = 0;
        for (int i = 0; i < n; i++) {
            boolean deload = (i % blockLen) == blockLen - 1;
            double multiplier;
            if (deload) {
                multiplier = deloadPct / 100.0;
            } else {
                multiplier = 1.0 + (increasePct / 100.0) * buildIndex;
                buildIndex++;
            }
            LocalDate target = firstWeekStart.plusWeeks(i);
            if (target.equals(sourceWeekStart)) {
                continue; // ne pas réécrire la semaine source
            }
            created += copyWeek(clubId, athleteId, sourceWeekStart, target, multiplier);
        }
        log.info("Mésocycle généré athlète={} : {} séance(s) sur {} semaines (à partir de {})",
                athleteId, created, n, firstWeekStart);
        return created;
    }

    /** Recopie une semaine de séances en mettant la charge à l'échelle (facteur multiplicatif). */
    private int copyWeek(UUID clubId, UUID athleteId, LocalDate sourceWeekStart,
                         LocalDate targetWeekStart, double multiplier) {
        List<Workout> source = workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        clubId, athleteId, sourceWeekStart, sourceWeekStart.plusDays(6));
        int created = 0;
        for (Workout w : source) {
            long offset = ChronoUnit.DAYS.between(sourceWeekStart, w.getScheduledDate());
            Workout copy = new Workout();
            copy.setClub(w.getClub());
            copy.setAthlete(w.getAthlete());
            copy.setStatus(WorkoutStatus.PLANNED);
            copy.setScheduledDate(targetWeekStart.plusDays(offset));
            copy.setType(w.getType());
            copy.setTitle(w.getTitle());
            copy.setNotes(w.getNotes());
            copy.setTargetDistanceM(scale(w.getTargetDistanceM(), multiplier));
            copy.setTargetDurationS(scale(w.getTargetDurationS(), multiplier));
            copy.setSourceTemplateId(w.getSourceTemplateId());
            // Le snapshot/cibles ne sont recopiés qu'à l'identique (multiplier 1.0) : une mise à
            // l'échelle invaliderait les cibles figées calculées.
            if (multiplier == 1.0) {
                copy.setSessionSnapshot(w.getSessionSnapshot());
                copy.setCalculatedPaces(w.getCalculatedPaces());
            }
            for (WorkoutStep s : w.getSteps()) {
                WorkoutStep ns = new WorkoutStep();
                ns.setWorkout(copy);
                ns.setOrderIndex(s.getOrderIndex());
                ns.setStepType(s.getStepType());
                ns.setRepetitions(s.getRepetitions());
                ns.setZone(s.getZone());
                ns.setDistanceM(scale(s.getDistanceM(), multiplier));
                ns.setDurationS(scale(s.getDurationS(), multiplier));
                ns.setNotes(s.getNotes());
                copy.getSteps().add(ns);
            }
            workoutRepository.save(copy);
            created++;
        }
        return created;
    }

    private Integer scale(Integer value, double multiplier) {
        if (value == null || multiplier == 1.0) {
            return value;
        }
        return (int) Math.round(value * multiplier);
    }

    /**
     * Crée une séance prescrite depuis la bibliothèque avec snapshot figé + cibles calculées
     * (cf. DARI Lab — copie figée au moment de l'assignation).
     */
    @Transactional
    public WorkoutResponse createPrescribed(UUID clubId, UUID athleteId, PrescribedWorkout data) {
        Athlete athlete = athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));

        Workout workout = new Workout();
        workout.setClub(athlete.getClub());
        workout.setAthlete(athlete);
        workout.setStatus(WorkoutStatus.PLANNED);
        workout.setScheduledDate(data.date());
        workout.setType(data.type());
        workout.setTitle(data.title());
        workout.setNotes(data.notes());
        workout.setTargetDistanceM(data.targetDistanceM());
        workout.setTargetDurationS(data.targetDurationS());
        workout.setSourceTemplateId(data.sourceTemplateId());
        workout.setSessionSnapshot(data.snapshotJson());
        workout.setCalculatedPaces(data.calculatedJson());

        workout = workoutRepository.save(workout);
        log.info("Séance prescrite {} depuis modèle {} (athlète={})",
                workout.getId(), data.sourceTemplateId(), athleteId);
        notificationService.notifyWorkoutPlanned(workout);
        return WorkoutResponse.from(workout);
    }

    /** Prescription figée d'une séance (snapshot + cibles calculées) — vue coach. */
    public WorkoutPrescriptionResponse prescription(UUID clubId, UUID workoutId) {
        return toPrescription(require(clubId, workoutId));
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
    public WorkoutResponse submitFeedback(UUID athleteId, UUID workoutId, WorkoutStatus status,
                                          Integer rpe, Integer fatigue, Integer pain, String comment) {
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
        workout.setFatigue(fatigue);
        workout.setPain(pain);
        workout.setAthleteComment(comment);
        notificationService.notifyAthleteFeedback(workout);
        return WorkoutResponse.from(workout);
    }

    /**
     * Déplacement d'une séance par l'athlète : change la date et marque {@code movedByAthlete}.
     * L'athlète peut déplacer mais jamais modifier le contenu (cf. DARI Lab).
     */
    @Transactional
    public WorkoutResponse moveByAthlete(UUID athleteId, UUID workoutId, LocalDate date) {
        Workout workout = workoutRepository.findByIdAndAthleteId(workoutId, athleteId)
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
        if (workout.getOriginalDate() == null) {
            workout.setOriginalDate(workout.getScheduledDate());
        }
        workout.setScheduledDate(date);
        workout.setMovedByAthlete(true);
        return WorkoutResponse.from(workout);
    }

    /** Prescription figée d'une séance — vue athlète (scopée par athleteId). */
    public WorkoutPrescriptionResponse prescriptionForAthlete(UUID athleteId, UUID workoutId) {
        Workout workout = workoutRepository.findByIdAndAthleteId(workoutId, athleteId)
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
        return toPrescription(workout);
    }

    private WorkoutPrescriptionResponse toPrescription(Workout w) {
        SessionStructure snapshot = readJson(w.getSessionSnapshot(), SessionStructure.class);
        CalculatedSessionResponse calculated = readJson(w.getCalculatedPaces(), CalculatedSessionResponse.class);
        return new WorkoutPrescriptionResponse(
                snapshot == null ? SessionStructure.empty() : snapshot, calculated);
    }

    private <T> T readJson(String json, Class<T> type) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
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
