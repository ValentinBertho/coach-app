package com.coachrun.controller;

import com.coachrun.dto.request.WorkoutRequest;
import com.coachrun.dto.request.WorkoutStatusRequest;
import com.coachrun.dto.response.WorkoutResponse;
import com.coachrun.service.WorkoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Séances prescrites d'un athlète (calendrier + éditeur structuré).
 * Scoping tenant systématique via @clubAccessValidator.
 */
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}/workouts")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class WorkoutController {

    private final WorkoutService workoutService;

    @GetMapping
    public List<WorkoutResponse> calendar(
            @PathVariable UUID clubId,
            @PathVariable UUID athleteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return workoutService.calendar(clubId, athleteId, from, to);
    }

    @GetMapping("/{workoutId}")
    public WorkoutResponse get(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                               @PathVariable UUID workoutId) {
        return workoutService.get(clubId, workoutId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutResponse create(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                  @Valid @RequestBody WorkoutRequest request) {
        return workoutService.create(clubId, athleteId, request);
    }

    @PutMapping("/{workoutId}")
    public WorkoutResponse update(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                  @PathVariable UUID workoutId, @Valid @RequestBody WorkoutRequest request) {
        return workoutService.update(clubId, workoutId, request);
    }

    @PatchMapping("/{workoutId}/status")
    public WorkoutResponse updateStatus(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                        @PathVariable UUID workoutId,
                                        @Valid @RequestBody WorkoutStatusRequest request) {
        return workoutService.updateStatus(clubId, workoutId, request.status());
    }

    @DeleteMapping("/{workoutId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                       @PathVariable UUID workoutId) {
        workoutService.delete(clubId, workoutId);
    }
}
