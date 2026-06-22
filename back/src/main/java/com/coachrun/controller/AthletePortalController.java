package com.coachrun.controller;

import com.coachrun.dto.request.WorkoutFeedbackRequest;
import com.coachrun.dto.response.AthleteExportResponse;
import com.coachrun.dto.response.UserResponse;
import com.coachrun.dto.response.WorkoutResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.AuthService;
import com.coachrun.service.GdprService;
import com.coachrun.service.WorkoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Portail athlète (PWA). Scoping par l'athleteId du principal — l'athlète n'accède
 * qu'à ses propres séances (jamais celles d'un autre athlète du club).
 */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ATHLETE')")
public class AthletePortalController {

    private final WorkoutService workoutService;
    private final AuthService authService;
    private final GdprService gdprService;
    private final com.coachrun.service.RaceObjectiveService raceService;
    private final com.coachrun.service.MessageService messageService;

    @GetMapping
    public UserResponse profile(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.currentUser(principal.userId());
    }

    @GetMapping("/today")
    public List<WorkoutResponse> today(@AuthenticationPrincipal AuthPrincipal principal,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now();
        return workoutService.todayForAthlete(principal.athleteId(), day);
    }

    @GetMapping("/workouts")
    public List<WorkoutResponse> workouts(@AuthenticationPrincipal AuthPrincipal principal,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return workoutService.athleteCalendar(principal.athleteId(), from, to);
    }

    @PatchMapping("/workouts/{workoutId}/feedback")
    public WorkoutResponse feedback(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable UUID workoutId,
                                    @Valid @RequestBody WorkoutFeedbackRequest request) {
        return workoutService.submitFeedback(
                principal.athleteId(), workoutId, request.status(), request.rpe(), request.comment());
    }

    /** Prochaine course cible (compte à rebours J-XX). 204 si aucune. */
    @GetMapping("/next-race")
    public org.springframework.http.ResponseEntity<com.coachrun.dto.response.RaceObjectiveResponse> nextRace(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return raceService.nextRace(principal.athleteId())
                .map(org.springframework.http.ResponseEntity::ok)
                .orElseGet(() -> org.springframework.http.ResponseEntity.noContent().build());
    }

    /** Messagerie : fil de discussion avec le coach. */
    @GetMapping("/messages")
    public java.util.List<com.coachrun.dto.response.MessageResponse> messages(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return messageService.athleteThread(principal.athleteId());
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public com.coachrun.dto.response.MessageResponse sendMessage(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody com.coachrun.dto.request.MessageRequest request) {
        return messageService.athleteSend(principal.athleteId(), principal, request);
    }

    /** RGPD — portabilité : export des données personnelles de l'athlète. */
    @GetMapping("/export")
    public AthleteExportResponse export(@AuthenticationPrincipal AuthPrincipal principal) {
        return gdprService.export(principal.athleteId());
    }

    /** RGPD — droit à l'oubli : suppression du compte et de toutes les données. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal AuthPrincipal principal) {
        gdprService.deleteAthleteData(principal.athleteId());
    }
}
