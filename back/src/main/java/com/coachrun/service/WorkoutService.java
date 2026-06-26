package com.coachrun.service;

import com.coachrun.dto.request.GenerateMesocycleRequest;
import com.coachrun.dto.request.WorkoutRequest;
import com.coachrun.dto.request.WorkoutStepRequest;
import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.dto.response.GroupApplyResponse;
import com.coachrun.dto.response.WorkoutPrescriptionResponse;
import com.coachrun.dto.response.WorkoutResponse;
import com.coachrun.dto.session.PrescribedWorkout;
import com.coachrun.dto.session.SessionStructure;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.MesocycleTemplate;
import com.coachrun.entity.Workout;
import com.coachrun.entity.WorkoutStep;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.PermissionLevel;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.MesocycleTemplateRepository;
import com.coachrun.repository.TrainingGroupRepository;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.security.AthleteAccessValidator;
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
    private final MesocycleTemplateRepository mesocycleTemplateRepository;
    private final TrainingGroupRepository groupRepository;
    private final AthleteAccessValidator accessValidator;
    private final ObjectMapper objectMapper;
    private final com.coachrun.repository.RunDrillRepository runDrillRepository;

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
        return create(clubId, athleteId, request, null);
    }

    /** Création avec rattachement optionnel à un plan ({@code planId}) pour le suivi d'avancement. */
    @Transactional
    public WorkoutResponse create(UUID clubId, UUID athleteId, WorkoutRequest request, UUID planId) {
        Athlete athlete = athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));

        Workout workout = new Workout();
        workout.setClub(athlete.getClub());
        workout.setAthlete(athlete);
        workout.setStatus(WorkoutStatus.PLANNED);
        workout.setPlanId(planId);
        apply(workout, request);

        workout = workoutRepository.save(workout);
        log.info("Séance créée {} (athlète={}, plan={})", workout.getId(), athleteId, planId);
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

    /** Paramètres de périodisation résolus (depuis un « méso type » ou la requête directe). */
    private record MesoParams(int weeks, double increasePct, int deloadEvery, double deloadPct) { }

    /**
     * Résout les paramètres de périodisation : depuis le modèle de mésocycle s'il est fourni,
     * sinon depuis la requête (avec valeurs par défaut). {@code weeks} est alors obligatoire.
     */
    private MesoParams resolveParams(UUID clubId, GenerateMesocycleRequest req) {
        if (req.mesocycleTemplateId() != null) {
            MesocycleTemplate t = mesocycleTemplateRepository
                    .findByIdAndClubId(req.mesocycleTemplateId(), clubId)
                    .orElseThrow(() -> new NotFoundException("Modèle de mésocycle introuvable."));
            return new MesoParams(t.getWeeks(), t.getIncreasePct(), t.getDeloadEvery(), t.getDeloadPct());
        }
        if (req.weeks() == null) {
            throw new ConflictException("Indiquez un nombre de semaines ou choisissez un modèle de mésocycle.");
        }
        return new MesoParams(req.weeks(), req.increasePctOrDefault(),
                req.deloadEveryOrDefault(), req.deloadPctOrDefault());
    }

    /** Génération de mésocycle pour un athlète à partir d'une requête (modèle ou paramètres directs). */
    @Transactional
    public int generateMesocycle(UUID clubId, UUID athleteId, GenerateMesocycleRequest req) {
        MesoParams p = resolveParams(clubId, req);
        return generateMesocycle(clubId, athleteId, req.sourceWeekStart(), req.firstWeekStart(),
                p.weeks(), p.increasePct(), p.deloadEvery(), p.deloadPct());
    }

    /**
     * Génère le mésocycle pour <strong>tous les athlètes actifs d'un groupe</strong> accessibles en
     * écriture (les autres sont ignorés). Chaque athlète est projeté à partir de sa propre semaine
     * source ({@code sourceWeekStart}) ; gros gain de temps pour piloter un groupe homogène.
     */
    @Transactional
    public GroupApplyResponse generateMesocycleForGroup(UUID clubId, UUID groupId,
                                                        GenerateMesocycleRequest req, UUID coachId) {
        groupRepository.findByIdAndClubId(groupId, clubId)
                .orElseThrow(() -> new NotFoundException("Groupe introuvable."));
        MesoParams p = resolveParams(clubId, req);
        List<Athlete> athletes = athleteRepository.findActiveByGroup(groupId, clubId, AthleteStatus.ACTIVE);
        int created = 0;
        int skipped = 0;
        int applied = 0;
        for (Athlete a : athletes) {
            boolean canWrite = accessValidator.effectiveLevel(coachId, a.getId())
                    .map(l -> l.atLeast(PermissionLevel.WRITE)).orElse(false);
            if (!canWrite) {
                skipped++;
                continue;
            }
            created += generateMesocycle(clubId, a.getId(), req.sourceWeekStart(), req.firstWeekStart(),
                    p.weeks(), p.increasePct(), p.deloadEvery(), p.deloadPct());
            applied++;
        }
        log.info("Mésocycle de groupe généré (groupe={}) : {} athlète(s), {} ignoré(s), {} séance(s)",
                groupId, applied, skipped, created);
        return new GroupApplyResponse(applied, skipped, created);
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
        SessionStructure safe = snapshot == null ? SessionStructure.empty() : snapshot;
        return new WorkoutPrescriptionResponse(safe, calculated, resolveDrills(safe, w.getClub().getId()));
    }

    /** Résout les éducatifs (gammes) référencés par les blocs du snapshot, scopés au club. */
    private java.util.List<com.coachrun.dto.response.RunDrillResponse> resolveDrills(
            SessionStructure s, UUID clubId) {
        java.util.LinkedHashSet<UUID> ids = new java.util.LinkedHashSet<>();
        java.util.stream.Stream.of(s.warmup(), s.main(), s.cooldown())
                .filter(java.util.Objects::nonNull)
                .flatMap(java.util.List::stream)
                .filter(b -> b.drillIds() != null)
                .forEach(b -> ids.addAll(b.drillIds()));
        if (ids.isEmpty()) {
            return java.util.List.of();
        }
        return ids.stream()
                .map(id -> runDrillRepository.findByIdAndClubId(id, clubId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(com.coachrun.dto.response.RunDrillResponse::of)
                .toList();
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
