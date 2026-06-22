package com.coachrun.service;

import com.coachrun.dto.request.PlanItemDto;
import com.coachrun.dto.request.TrainingPlanRequest;
import com.coachrun.dto.response.TrainingPlanResponse;
import com.coachrun.entity.TrainingPlan;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.TrainingPlanRepository;
import com.coachrun.repository.WorkoutTemplateRepository;
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
    private final ObjectMapper objectMapper;

    public List<TrainingPlanResponse> list(UUID clubId) {
        return planRepository.findByClubIdOrderByNameAsc(clubId).stream()
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

    /** Applique le plan : génère une séance par item à partir de la date de départ. */
    @Transactional
    public int applyToAthlete(UUID clubId, UUID planId, UUID athleteId, LocalDate startDate) {
        TrainingPlan p = require(clubId, planId);
        List<PlanItemDto> items = readItems(p.getItemsJson());
        int created = 0;
        for (PlanItemDto item : items) {
            LocalDate date = startDate.plusWeeks(item.weekIndex()).plusDays(item.dayOfWeek() - 1L);
            templateService.apply(clubId, item.templateId(), athleteId, date);
            created++;
        }
        return created;
    }

    private TrainingPlan require(UUID clubId, UUID id) {
        return planRepository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Plan introuvable."));
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
