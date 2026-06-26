package com.coachrun.service;

import com.coachrun.dto.request.WorkoutRequest;
import com.coachrun.dto.request.WorkoutStepRequest;
import com.coachrun.dto.request.WorkoutTemplateRequest;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.dto.response.GroupApplyResponse;
import com.coachrun.dto.response.WorkoutResponse;
import com.coachrun.dto.response.WorkoutTemplateResponse;
import com.coachrun.entity.WorkoutTemplate;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.WorkoutTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/** Bibliothèque de séances : CRUD scopé club + application au calendrier d'un athlète. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkoutTemplateService {

    private final WorkoutTemplateRepository templateRepository;
    private final ClubRepository clubRepository;
    private final WorkoutService workoutService;
    private final ObjectMapper objectMapper;
    private final CourseSessionService courseSessionService;
    private final com.coachrun.repository.AthleteRepository athleteRepository;
    private final com.coachrun.security.AthleteAccessValidator accessValidator;

    public PageResponse<WorkoutTemplateResponse> list(UUID clubId, String q, Pageable pageable) {
        var page = StringUtils.hasText(q)
                ? templateRepository.findByClubIdAndNameContainingIgnoreCase(clubId, q.trim(), pageable)
                : templateRepository.findByClubId(clubId, pageable);
        return PageResponse.from(page, this::toResponse);
    }

    public WorkoutTemplateResponse get(UUID clubId, UUID id) {
        return toResponse(require(clubId, id));
    }

    @Transactional
    public WorkoutTemplateResponse create(UUID clubId, WorkoutTemplateRequest request) {
        WorkoutTemplate t = new WorkoutTemplate();
        t.setClub(clubRepository.getReferenceById(clubId));
        apply(t, request);
        return toResponse(templateRepository.save(t));
    }

    @Transactional
    public WorkoutTemplateResponse update(UUID clubId, UUID id, WorkoutTemplateRequest request) {
        WorkoutTemplate t = require(clubId, id);
        apply(t, request);
        return toResponse(t);
    }

    @Transactional
    public void delete(UUID clubId, UUID id) {
        templateRepository.delete(require(clubId, id));
    }

    /**
     * Crée une séance datée pour un athlète à partir du modèle (1 clic). Utilise le chemin
     * structuré DARI Lab (snapshot figé + cibles en fourchettes) dès que le modèle a une structure ;
     * sinon repli sur les étapes legacy. Cohérent avec le drag &amp; drop du calendrier.
     */
    @Transactional
    public WorkoutResponse apply(UUID clubId, UUID templateId, UUID athleteId, java.time.LocalDate date) {
        WorkoutTemplate t = require(clubId, templateId);
        if (org.springframework.util.StringUtils.hasText(t.getStructureJson())) {
            return courseSessionService.scheduleForAthlete(clubId, athleteId, templateId, date);
        }
        return apply(clubId, templateId, athleteId, date, null);
    }

    /**
     * Applique un modèle à tous les athlètes actifs d'un groupe sur une date donnée. Les athlètes
     * sur lesquels le coach n'a pas le droit d'écriture sont ignorés (jamais d'échec global).
     */
    @Transactional
    public GroupApplyResponse applyToGroup(UUID clubId, UUID templateId, UUID groupId,
                                           java.time.LocalDate date, UUID coachId) {
        require(clubId, templateId); // valide le modèle dans le club
        var athletes = athleteRepository.findActiveByGroup(
                groupId, clubId, com.coachrun.entity.enums.AthleteStatus.ACTIVE);
        int created = 0;
        int skipped = 0;
        for (var a : athletes) {
            boolean canWrite = accessValidator.effectiveLevel(coachId, a.getId())
                    .map(l -> l.atLeast(com.coachrun.entity.enums.PermissionLevel.WRITE))
                    .orElse(false);
            if (!canWrite) {
                skipped++;
                continue;
            }
            apply(clubId, templateId, a.getId(), date);
            created++;
        }
        return new GroupApplyResponse(created, skipped, created);
    }

    /** Applique un modèle en rattachant la séance générée à un plan ({@code planId}) pour le suivi. */
    @Transactional
    public WorkoutResponse apply(UUID clubId, UUID templateId, UUID athleteId,
                                 java.time.LocalDate date, java.util.UUID planId) {
        return apply(clubId, templateId, athleteId, date, planId, 1.0);
    }

    /**
     * Applique un modèle en mettant la charge (distance/durée) à l'échelle d'un facteur
     * {@code multiplier} — pour la périodisation d'un plan (montée de charge / décharge).
     */
    @Transactional
    public WorkoutResponse apply(UUID clubId, UUID templateId, UUID athleteId,
                                 java.time.LocalDate date, java.util.UUID planId, double multiplier) {
        WorkoutTemplate t = require(clubId, templateId);
        java.util.List<com.coachrun.dto.request.WorkoutStepRequest> steps = readSteps(t.getStepsJson())
                .stream().map(s -> scaleStep(s, multiplier)).toList();
        WorkoutRequest req = new WorkoutRequest(
                date, t.getType(), t.getTitle(), t.getNotes(),
                scale(t.getTargetDistanceM(), multiplier), scale(t.getTargetDurationS(), multiplier), steps);
        return workoutService.create(clubId, athleteId, req, planId);
    }

    private com.coachrun.dto.request.WorkoutStepRequest scaleStep(
            com.coachrun.dto.request.WorkoutStepRequest s, double multiplier) {
        if (multiplier == 1.0) {
            return s;
        }
        return new com.coachrun.dto.request.WorkoutStepRequest(
                s.stepType(), s.repetitions(), s.zone(),
                scale(s.distanceM(), multiplier), scale(s.durationS(), multiplier), s.notes());
    }

    private Integer scale(Integer value, double multiplier) {
        if (value == null || multiplier == 1.0) {
            return value;
        }
        return (int) Math.round(value * multiplier);
    }

    private WorkoutTemplate require(UUID clubId, UUID id) {
        return templateRepository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Modèle introuvable."));
    }

    private void apply(WorkoutTemplate t, WorkoutTemplateRequest request) {
        t.setName(request.name());
        t.setType(request.type());
        t.setTitle(request.title());
        t.setNotes(request.notes());
        t.setTargetDistanceM(request.targetDistanceM());
        t.setTargetDurationS(request.targetDurationS());
        t.setStepsJson(writeSteps(request.steps()));
    }

    private WorkoutTemplateResponse toResponse(WorkoutTemplate t) {
        return WorkoutTemplateResponse.of(t, readSteps(t.getStepsJson()));
    }

    private String writeSteps(List<WorkoutStepRequest> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (Exception e) {
            throw new IllegalStateException("Sérialisation des étapes impossible.", e);
        }
    }

    private List<WorkoutStepRequest> readSteps(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<WorkoutStepRequest>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }
}
