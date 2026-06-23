package com.coachrun.service;

import com.coachrun.dto.request.CourseStructureRequest;
import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.dto.response.CourseStructureResponse;
import com.coachrun.dto.response.WorkoutResponse;
import com.coachrun.dto.session.PrescribedWorkout;
import com.coachrun.dto.session.SessionStructure;
import com.coachrun.entity.WorkoutTemplate;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.SessionCategoryRepository;
import com.coachrun.repository.WorkoutTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Structure DARI Lab d'une séance de bibliothèque (blocs en fourchettes) + calcul pour un athlète.
 * La structure est sérialisée en JSON dans {@code workout_templates.structure_json}, à l'image
 * des étapes existantes (pas de table dédiée).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseSessionService {

    private final WorkoutTemplateRepository templateRepository;
    private final SessionCategoryRepository categoryRepository;
    private final SessionCalculatorService calculatorService;
    private final WorkoutService workoutService;
    private final ObjectMapper objectMapper;

    public CourseStructureResponse getStructure(UUID clubId, UUID templateId) {
        WorkoutTemplate t = require(clubId, templateId);
        return CourseStructureResponse.of(t, readStructure(t.getStructureJson()));
    }

    @Transactional
    public CourseStructureResponse putStructure(UUID clubId, UUID templateId, CourseStructureRequest req) {
        WorkoutTemplate t = require(clubId, templateId);
        if (req.discipline() != null) {
            t.setDiscipline(req.discipline());
        }
        if (req.favorite() != null) {
            t.setFavorite(req.favorite());
        }
        if (req.categoryId() != null) {
            t.setCategory(categoryRepository.findByIdAndClubId(req.categoryId(), clubId)
                    .orElseThrow(() -> new NotFoundException("Catégorie introuvable.")));
        } else {
            t.setCategory(null);
        }
        SessionStructure structure = req.structure() == null ? SessionStructure.empty() : req.structure();
        t.setStructureJson(writeStructure(structure));
        return CourseStructureResponse.of(t, structure);
    }

    /** Calcule toute la séance pour un athlète donné (allures/FC/RPE par bloc + totaux). */
    public CalculatedSessionResponse calculateForAthlete(UUID clubId, UUID athleteId, UUID templateId) {
        WorkoutTemplate t = require(clubId, templateId);
        return calculatorService.calculateSession(clubId, athleteId, readStructure(t.getStructureJson()));
    }

    /**
     * Assigne une séance de bibliothèque au calendrier d'un athlète : fige la prescription
     * (snapshot) et les cibles calculées, incrémente le compteur d'usage du modèle.
     */
    @Transactional
    public WorkoutResponse scheduleForAthlete(UUID clubId, UUID athleteId, UUID templateId, LocalDate date) {
        WorkoutTemplate t = require(clubId, templateId);
        SessionStructure structure = readStructure(t.getStructureJson());
        CalculatedSessionResponse calc = calculatorService.calculateSession(clubId, athleteId, structure);

        String snapshotJson = writeStructure(structure);
        String calculatedJson = writeJson(calc);
        Integer distance = calc.totalDistanceM() != null ? calc.totalDistanceM() : t.getTargetDistanceM();
        Integer duration = calc.totalDurationS() != null ? calc.totalDurationS() : t.getTargetDurationS();

        t.setUseCount(t.getUseCount() + 1);
        t.setLastUsedAt(Instant.now());

        PrescribedWorkout data = new PrescribedWorkout(
                date, t.getType(), t.getTitle(), t.getNotes(), distance, duration,
                t.getId(), snapshotJson, calculatedJson);
        return workoutService.createPrescribed(clubId, athleteId, data);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Sérialisation des cibles calculées impossible.", e);
        }
    }

    private WorkoutTemplate require(UUID clubId, UUID templateId) {
        return templateRepository.findByIdAndClubId(templateId, clubId)
                .orElseThrow(() -> new NotFoundException("Modèle introuvable."));
    }

    private String writeStructure(SessionStructure structure) {
        try {
            return objectMapper.writeValueAsString(structure);
        } catch (Exception e) {
            throw new IllegalStateException("Sérialisation de la structure impossible.", e);
        }
    }

    private SessionStructure readStructure(String json) {
        if (!StringUtils.hasText(json)) {
            return SessionStructure.empty();
        }
        try {
            return objectMapper.readValue(json, SessionStructure.class);
        } catch (Exception e) {
            return SessionStructure.empty();
        }
    }
}
