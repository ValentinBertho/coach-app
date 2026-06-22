package com.coachrun.service;

import com.coachrun.dto.request.WorkoutRequest;
import com.coachrun.dto.request.WorkoutStepRequest;
import com.coachrun.dto.request.WorkoutTemplateRequest;
import com.coachrun.dto.response.PageResponse;
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

    /** Crée une séance datée pour un athlète à partir du modèle (1 clic). */
    @Transactional
    public WorkoutResponse apply(UUID clubId, UUID templateId, UUID athleteId, java.time.LocalDate date) {
        WorkoutTemplate t = require(clubId, templateId);
        WorkoutRequest req = new WorkoutRequest(
                date, t.getType(), t.getTitle(), t.getNotes(),
                t.getTargetDistanceM(), t.getTargetDurationS(), readSteps(t.getStepsJson()));
        return workoutService.create(clubId, athleteId, req);
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
