package com.coachrun.service;

import com.coachrun.dto.request.PlanItemDto;
import com.coachrun.dto.request.TrainingPlanRequest;
import com.coachrun.dto.response.GroupApplyResponse;
import com.coachrun.dto.response.PlanProgressResponse;
import com.coachrun.dto.response.TrainingPlanResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.PlanAssignment;
import com.coachrun.entity.TrainingPlan;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.PermissionLevel;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.PlanAssignmentRepository;
import com.coachrun.repository.TrainingGroupRepository;
import com.coachrun.repository.TrainingPlanRepository;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.repository.WorkoutTemplateRepository;
import com.coachrun.security.AthleteAccessValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Plans périodisés : CRUD scopé club + application à un athlète (génère les séances). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingPlanService {

    private final TrainingPlanRepository planRepository;
    private final ClubRepository clubRepository;
    private final WorkoutTemplateRepository templateRepository;
    private final WorkoutTemplateService templateService;
    private final AthleteRepository athleteRepository;
    private final TrainingGroupRepository groupRepository;
    private final AthleteAccessValidator accessValidator;
    private final PlanAssignmentRepository assignmentRepository;
    private final WorkoutRepository workoutRepository;
    private final ObjectMapper objectMapper;

    public List<TrainingPlanResponse> list(UUID clubId) {
        return planRepository.findByClubIdOrderByNameAsc(clubId).stream()
                .map(p -> toResponse(clubId, p)).toList();
    }

    /** Plans attribués à un athlète donné (relation many-to-many). */
    public List<TrainingPlanResponse> listForAthlete(UUID clubId, UUID athleteId) {
        requireAthlete(clubId, athleteId);
        return planRepository.findByAthletes_IdOrderByNameAsc(athleteId).stream()
                .map(p -> toResponse(clubId, p)).toList();
    }

    public TrainingPlanResponse get(UUID clubId, UUID id) {
        return toResponse(clubId, require(clubId, id));
    }

    @Transactional
    public TrainingPlanResponse create(UUID clubId, TrainingPlanRequest request) {
        TrainingPlan p = new TrainingPlan();
        p.setClub(clubRepository.getReferenceById(clubId));
        apply(p, request);
        return toResponse(clubId, planRepository.save(p));
    }

    @Transactional
    public TrainingPlanResponse update(UUID clubId, UUID id, TrainingPlanRequest request) {
        TrainingPlan p = require(clubId, id);
        apply(p, request);
        return toResponse(clubId, p);
    }

    @Transactional
    public void delete(UUID clubId, UUID id) {
        planRepository.delete(require(clubId, id));
    }

    /**
     * Applique le plan : génère une séance par item à partir de la date de départ, en
     * <strong>rattachant chaque séance au plan</strong> ({@code planId}) et en enregistrant
     * l'attribution datée (suivi d'avancement). Idempotent : les séances encore planifiées d'une
     * application précédente sont purgées d'abord (l'historique réalisé est préservé).
     */
    @Transactional
    public int applyToAthlete(UUID clubId, UUID planId, UUID athleteId, LocalDate startDate) {
        TrainingPlan p = require(clubId, planId);
        Athlete athlete = requireAthlete(clubId, athleteId);
        p.getAthletes().add(athlete);

        // Idempotence : on retire les séances encore planifiées de ce plan avant de regénérer.
        workoutRepository.deleteByPlanIdAndAthleteIdAndStatus(planId, athleteId, WorkoutStatus.PLANNED);

        // Attribution datée (source de vérité du suivi) : upsert.
        PlanAssignment assignment = assignmentRepository.findByPlanIdAndAthleteId(planId, athleteId)
                .orElseGet(() -> {
                    PlanAssignment a = new PlanAssignment();
                    a.setPlan(p);
                    a.setAthlete(athlete);
                    return a;
                });
        assignment.setStartDate(startDate);
        assignmentRepository.save(assignment);

        List<PlanItemDto> items = readItems(p.getItemsJson());
        int created = 0;
        for (PlanItemDto item : items) {
            LocalDate date = startDate.plusWeeks(item.weekIndex()).plusDays(item.dayOfWeek() - 1L);
            templateService.apply(clubId, item.templateId(), athleteId, date, planId);
            created++;
        }
        return created;
    }

    /** Avancement d'un plan pour un athlète : semaine courante et part de séances réalisées. */
    public PlanProgressResponse progress(UUID clubId, UUID planId, UUID athleteId) {
        TrainingPlan p = require(clubId, planId);
        requireAthlete(clubId, athleteId);
        PlanAssignment assignment = assignmentRepository.findByPlanIdAndAthleteId(planId, athleteId)
                .orElseThrow(() -> new NotFoundException("Plan non attribué à cet athlète."));

        LocalDate start = assignment.getStartDate();
        int weeks = Math.max(1, p.getDurationWeeks());
        long elapsedWeeks = java.time.temporal.ChronoUnit.WEEKS.between(start, LocalDate.now());
        int currentWeek = (int) Math.max(1, Math.min(weeks, elapsedWeeks + 1));
        boolean finished = LocalDate.now().isAfter(start.plusWeeks(weeks));

        long total = workoutRepository.countByPlanIdAndAthleteId(planId, athleteId);
        long completed = workoutRepository.countByPlanIdAndAthleteIdAndStatus(
                planId, athleteId, WorkoutStatus.COMPLETED);
        int percent = total == 0 ? 0 : (int) Math.round(100.0 * completed / total);

        return new PlanProgressResponse(start, weeks, finished ? weeks : currentWeek,
                total, completed, percent, finished);
    }

    /**
     * Applique le plan à tous les athlètes <strong>actifs</strong> d'un groupe pour lesquels le
     * coach dispose d'un accès en écriture (les autres sont ignorés sans bloquer l'opération).
     * Gros gain de temps : un plan périodisé attribué à tout un groupe en une action.
     */
    @Transactional
    public GroupApplyResponse applyToGroup(UUID clubId, UUID planId, UUID groupId,
                                           LocalDate startDate, UUID coachId) {
        require(clubId, planId); // valide l'existence/scope du plan
        groupRepository.findByIdAndClubId(groupId, clubId)
                .orElseThrow(() -> new NotFoundException("Groupe introuvable."));
        List<Athlete> athletes = athleteRepository.findActiveByGroup(groupId, clubId, AthleteStatus.ACTIVE);
        int created = 0;
        int skipped = 0;
        int applied = 0;
        for (Athlete a : athletes) {
            if (!canWrite(coachId, a.getId())) {
                skipped++;
                continue;
            }
            created += applyToAthlete(clubId, planId, a.getId(), startDate);
            applied++;
        }
        return new GroupApplyResponse(applied, skipped, created);
    }

    private boolean canWrite(UUID coachId, UUID athleteId) {
        return accessValidator.effectiveLevel(coachId, athleteId)
                .map(l -> l.atLeast(PermissionLevel.WRITE)).orElse(false);
    }

    /**
     * Retire l'attribution d'un plan à un athlète et fait le ménage : supprime les séances du plan
     * <strong>encore planifiées</strong> (l'historique réalisé est conservé) et l'attribution datée.
     */
    @Transactional
    public void unassignAthlete(UUID clubId, UUID planId, UUID athleteId) {
        TrainingPlan p = require(clubId, planId);
        p.getAthletes().removeIf(a -> a.getId().equals(athleteId));
        workoutRepository.deleteByPlanIdAndAthleteIdAndStatus(planId, athleteId, WorkoutStatus.PLANNED);
        assignmentRepository.deleteByPlanIdAndAthleteId(planId, athleteId);
    }

    private TrainingPlan require(UUID clubId, UUID id) {
        return planRepository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Plan introuvable."));
    }

    private Athlete requireAthlete(UUID clubId, UUID athleteId) {
        return athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
    }

    private void apply(TrainingPlan p, TrainingPlanRequest request) {
        p.setName(request.name());
        p.setDescription(request.description());
        p.setDurationWeeks(request.durationWeeks());
        try {
            p.setItemsJson(objectMapper.writeValueAsString(request.items()));
        } catch (Exception e) {
            throw new IllegalStateException("Sérialisation du plan impossible.", e);
        }
    }

    private TrainingPlanResponse toResponse(UUID clubId, TrainingPlan p) {
        List<TrainingPlanResponse.PlanItem> items = readItems(p.getItemsJson()).stream()
                .map(i -> new TrainingPlanResponse.PlanItem(
                        i.weekIndex(), i.dayOfWeek(), i.templateId(),
                        templateRepository.findByIdAndClubId(i.templateId(), clubId)
                                .map(t -> t.getName()).orElse("(modèle supprimé)")))
                .toList();
        return TrainingPlanResponse.of(p, items);
    }

    private List<PlanItemDto> readItems(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<PlanItemDto>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }
}
