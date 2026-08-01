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
    private final com.coachrun.repository.WorkoutRepository workoutRepository;
    private final com.coachrun.engine.PlannedLoadEngine plannedLoadEngine;
    private final SessionCategoryRepository categoryRepository;
    private final SessionCalculatorService calculatorService;
    private final WorkoutService workoutService;
    private final PrescriptionZoneMapper zoneMapper;
    private final ObjectMapper objectMapper;

    /**
     * Structure d'un modèle pour l'éditeur. Migration douce Z4 : les blocs legacy ({@code ref + %})
     * reçoivent à la lecture un {@code zoneId} déduit (les champs legacy sont conservés) afin que
     * l'éditeur épuré affiche la zone ; l'enregistrement persiste la migration.
     */
    @Transactional
    public CourseStructureResponse getStructure(UUID clubId, UUID templateId) {
        WorkoutTemplate t = require(clubId, templateId);
        return CourseStructureResponse.of(t, zoneMapper.withZones(clubId, readStructure(t.getStructureJson())));
    }

    /**
     * Enregistre la structure et, si le payload les porte, les métadonnées d'identité. Chaque
     * champ absent est laissé tel quel : l'éditeur auto-sauvegarde la structure toutes les
     * quelques secondes, et un champ manquant ne doit jamais valoir « efface ».
     */
    @Transactional
    public CourseStructureResponse putStructure(UUID clubId, UUID templateId, CourseStructureRequest req) {
        WorkoutTemplate t = require(clubId, templateId);
        if (StringUtils.hasText(req.name())) {
            t.setName(req.name().trim());
        }
        if (StringUtils.hasText(req.title())) {
            t.setTitle(req.title().trim());
        }
        if (req.discipline() != null) {
            t.setDiscipline(req.discipline());
        }
        if (req.favorite() != null) {
            t.setFavorite(req.favorite());
        }
        if (req.notes() != null) {
            t.setNotes(req.notes().isBlank() ? null : req.notes());
        }
        if (Boolean.TRUE.equals(req.clearCategory())) {
            t.setCategory(null);
        } else if (req.categoryId() != null) {
            t.setCategory(categoryRepository.findByIdAndClubId(req.categoryId(), clubId)
                    .orElseThrow(() -> new NotFoundException("Catégorie introuvable.")));
        }
        // Payload de renommage seul : la structure absente laisse les blocs en place.
        SessionStructure structure = req.structure() == null
                ? readStructure(t.getStructureJson())
                : req.structure();
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
                t.getId(), snapshotJson, calculatedJson, plannedLoadEngine.compute(calc));
        return workoutService.createPrescribed(clubId, athleteId, data);
    }

    /**
     * Verse dans la bibliothèque une séance construite directement au calendrier. Une séance
     * improvisée pour un athlète se retrouvait sans issue : la refaire de zéro pour en garder un
     * modèle. On recopie ici son snapshot tel quel dans un nouveau modèle du club.
     */
    @Transactional
    public CourseStructureResponse saveWorkoutAsTemplate(UUID clubId, UUID workoutId,
                                                         com.coachrun.dto.request.SaveAsTemplateRequest req) {
        com.coachrun.entity.Workout w = workoutRepository.findByIdAndClubId(workoutId, clubId)
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
        SessionStructure structure = readStructure(w.getSessionSnapshot());

        WorkoutTemplate t = new WorkoutTemplate();
        t.setClub(w.getClub());
        t.setName(req.name());
        t.setTitle(req.title());
        t.setType(w.getType());
        t.setNotes(w.getNotes());
        t.setTargetDistanceM(w.getTargetDistanceM());
        t.setTargetDurationS(w.getTargetDurationS());
        t.setStructureJson(writeStructure(structure));
        if (req.categoryId() != null) {
            t.setCategory(categoryRepository.findByIdAndClubId(req.categoryId(), clubId)
                    .orElseThrow(() -> new NotFoundException("Catégorie introuvable.")));
        }
        return CourseStructureResponse.of(templateRepository.save(t), structure);
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
