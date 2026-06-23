package com.coachrun.controller;

import com.coachrun.dto.request.CourseStructureRequest;
import com.coachrun.dto.request.ScheduleSessionRequest;
import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.dto.response.CourseStructureResponse;
import com.coachrun.dto.response.WorkoutResponse;
import com.coachrun.service.CourseSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Structure DARI Lab d'une séance de bibliothèque (blocs prescrits en fourchettes) et calcul
 * d'une séance pour un athlète donné. Complète le CRUD générique de {@code WorkoutTemplateController}.
 */
@Tag(name = "Séances course (structure DARI Lab)")
@RestController
@RequestMapping("/clubs/{clubId}")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class CourseSessionController {

    private final CourseSessionService courseSessionService;

    @GetMapping("/workout-templates/{templateId}/structure")
    public CourseStructureResponse getStructure(@PathVariable UUID clubId, @PathVariable UUID templateId) {
        return courseSessionService.getStructure(clubId, templateId);
    }

    @PutMapping("/workout-templates/{templateId}/structure")
    public CourseStructureResponse putStructure(@PathVariable UUID clubId, @PathVariable UUID templateId,
                                                @Valid @RequestBody CourseStructureRequest request) {
        return courseSessionService.putStructure(clubId, templateId, request);
    }

    /** Séance entièrement calculée (allures/FC/RPE/totaux) pour un athlète. */
    @GetMapping("/athletes/{athleteId}/workout-templates/{templateId}/calculated")
    public CalculatedSessionResponse calculated(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                                @PathVariable UUID templateId) {
        return courseSessionService.calculateForAthlete(clubId, athleteId, templateId);
    }

    /** Assigne la séance au calendrier de l'athlète (snapshot figé + cibles calculées). */
    @PostMapping("/athletes/{athleteId}/workout-templates/{templateId}/schedule")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutResponse schedule(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                    @PathVariable UUID templateId,
                                    @Valid @RequestBody ScheduleSessionRequest request) {
        return courseSessionService.scheduleForAthlete(clubId, athleteId, templateId, request.date());
    }
}
