package com.coachrun.controller;

import com.coachrun.dto.request.WorkoutRequest;
import com.coachrun.dto.request.WorkoutRescheduleRequest;
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
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canRead(authentication, #athleteId)")
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

    /** Prescription figée (snapshot des blocs + cibles calculées) d'une séance planifiée. */
    @GetMapping("/{workoutId}/prescription")
    public com.coachrun.dto.response.WorkoutPrescriptionResponse prescription(
            @PathVariable UUID clubId, @PathVariable UUID athleteId, @PathVariable UUID workoutId) {
        return workoutService.prescription(clubId, workoutId);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutResponse create(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                  @Valid @RequestBody WorkoutRequest request) {
        return workoutService.create(clubId, athleteId, request);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PutMapping("/{workoutId}")
    public WorkoutResponse update(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                  @PathVariable UUID workoutId, @Valid @RequestBody WorkoutRequest request) {
        return workoutService.update(clubId, workoutId, request);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PatchMapping("/{workoutId}/reschedule")
    public WorkoutResponse reschedule(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                      @PathVariable UUID workoutId,
                                      @Valid @RequestBody WorkoutRescheduleRequest request) {
        return workoutService.reschedule(clubId, workoutId, request.scheduledDate());
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PatchMapping("/{workoutId}/status")
    public WorkoutResponse updateStatus(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                        @PathVariable UUID workoutId,
                                        @Valid @RequestBody WorkoutStatusRequest request) {
        return workoutService.updateStatus(clubId, workoutId, request.status());
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @DeleteMapping("/{workoutId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                       @PathVariable UUID workoutId) {
        workoutService.delete(clubId, workoutId);
    }

    /** Planification en cycles : duplique une semaine de séances vers une autre semaine. */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping("/duplicate-week")
    public java.util.Map<String, Integer> duplicateWeek(
            @PathVariable UUID clubId, @PathVariable UUID athleteId,
            @Valid @RequestBody com.coachrun.dto.request.DuplicateWeekRequest request) {
        int created = workoutService.duplicateWeek(
                clubId, athleteId, request.sourceWeekStart(), request.targetWeekStart());
        return java.util.Map.of("created", created);
    }

    /** Périodisation assistée : génère un mésocycle progressif depuis une semaine type. */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping("/generate-mesocycle")
    public java.util.Map<String, Integer> generateMesocycle(
            @PathVariable UUID clubId, @PathVariable UUID athleteId,
            @Valid @RequestBody com.coachrun.dto.request.GenerateMesocycleRequest request) {
        int created = workoutService.generateMesocycle(
                clubId, athleteId, request.sourceWeekStart(), request.firstWeekStart(),
                request.weeks(), request.increasePctOrDefault(),
                request.deloadEveryOrDefault(), request.deloadPctOrDefault());
        return java.util.Map.of("created", created, "weeks", request.weeks());
    }
}
