package com.coachrun.controller;

import com.coachrun.dto.request.WorkoutRequest;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.dto.response.WorkoutResponse;
import com.coachrun.dto.response.WorkoutTemplateResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.OwnWorkoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Les séances que l'athlète s'ajoute lui-même (cf. {@link OwnWorkoutService}). */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ATHLETE')")
public class OwnWorkoutController {

    private final OwnWorkoutService ownWorkouts;

    @PostMapping("/own-workouts")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                  @Valid @RequestBody WorkoutRequest request) {
        return ownWorkouts.create(principal.athleteId(), request);
    }

    @DeleteMapping("/own-workouts/{workoutId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID workoutId) {
        ownWorkouts.delete(principal.athleteId(), workoutId);
    }

    @GetMapping("/catalog")
    public PageResponse<WorkoutTemplateResponse> catalog(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @RequestParam(required = false) String q) {
        return ownWorkouts.catalog(principal.athleteId(), q);
    }

    @PostMapping("/catalog/{templateId}/schedule")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutResponse schedule(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable UUID templateId,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ownWorkouts.schedule(principal.athleteId(), templateId, date);
    }
}
