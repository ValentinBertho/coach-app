package com.coachrun.controller;

import com.coachrun.dto.request.PpExerciseRequest;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.dto.response.PpExerciseResponse;
import com.coachrun.entity.enums.EquipmentType;
import com.coachrun.entity.enums.ExerciseCategory;
import com.coachrun.entity.enums.ExerciseLevel;
import com.coachrun.entity.enums.MuscleGroup;
import com.coachrun.service.PpExerciseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Bibliothèque d'exercices de préparation physique (cf. DARI Lab). Scoping tenant. */
@Tag(name = "Préparation physique — Exercices")
@RestController
@RequestMapping("/clubs/{clubId}/pp/exercises")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class PpExerciseController {

    private final PpExerciseService exerciseService;

    @GetMapping
    public PageResponse<PpExerciseResponse> list(
            @PathVariable UUID clubId,
            @RequestParam(required = false) ExerciseCategory category,
            @RequestParam(required = false) ExerciseLevel level,
            @RequestParam(required = false) MuscleGroup muscle,
            @RequestParam(required = false) EquipmentType equipment,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return exerciseService.search(clubId, category, level, muscle, equipment, q, pageable);
    }

    @GetMapping("/{id}")
    public PpExerciseResponse get(@PathVariable UUID clubId, @PathVariable UUID id) {
        return exerciseService.get(clubId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PpExerciseResponse create(@PathVariable UUID clubId,
                                     @Valid @RequestBody PpExerciseRequest request) {
        return exerciseService.create(clubId, request);
    }

    @PutMapping("/{id}")
    public PpExerciseResponse update(@PathVariable UUID clubId, @PathVariable UUID id,
                                     @Valid @RequestBody PpExerciseRequest request) {
        return exerciseService.update(clubId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID clubId, @PathVariable UUID id) {
        exerciseService.archive(clubId, id);
    }
}
