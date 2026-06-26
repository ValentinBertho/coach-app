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
    private final com.coachrun.service.MessageStreamService messageStreamService;
    private final com.coachrun.service.StrengthScheduleService strengthScheduleService;
    private final com.coachrun.service.StrengthResultService strengthResultService;
    private final com.coachrun.service.ProgressionService progressionService;
    private final com.coachrun.service.UnavailabilityService unavailabilityService;
    private final com.coachrun.service.Athlete1rmService oneRmService;
    private final com.coachrun.service.AthletePhysioService physioService;
    private final com.coachrun.service.AthleteLoadService loadService;
    private final com.coachrun.service.AnalyticsService analyticsService;
    private final com.coachrun.service.ActivityService activityService;
    private final com.coachrun.service.LactateTestService lactateTestService;
    private final com.coachrun.service.TrainingPlanService trainingPlanService;

    @GetMapping
    public UserResponse profile(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.currentUser(principal.userId());
    }

    /** Mon programme : plans attribués à l'athlète avec leur avancement. */
    @GetMapping("/plans")
    public List<com.coachrun.dto.response.AthletePlanResponse> myPlans(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return trainingPlanService.myPlans(principal.athleteId());
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
        return workoutService.submitFeedback(principal.athleteId(), workoutId, request.status(),
                request.rpe(), request.fatigue(), request.pain(), request.comment());
    }

    /** L'athlète déplace une séance (jamais la modifier) : change la date uniquement. */
    @PatchMapping("/workouts/{workoutId}/move")
    public WorkoutResponse move(@AuthenticationPrincipal AuthPrincipal principal,
                                @PathVariable UUID workoutId,
                                @Valid @RequestBody com.coachrun.dto.request.WorkoutRescheduleRequest request) {
        return workoutService.moveByAthlete(principal.athleteId(), workoutId, request.scheduledDate());
    }

    /** Prescription figée de la séance (snapshot + cibles calculées) — vue athlète. */
    @GetMapping("/workouts/{workoutId}/prescription")
    public com.coachrun.dto.response.WorkoutPrescriptionResponse prescription(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID workoutId) {
        return workoutService.prescriptionForAthlete(principal.athleteId(), workoutId);
    }

    // --- Préparation physique (séances de force planifiées) ------------------

    @GetMapping("/pp/scheduled")
    public java.util.List<com.coachrun.dto.response.ScheduledStrengthResponse> ppScheduled(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return strengthScheduleService.athleteCalendar(principal.athleteId(), from, to);
    }

    @GetMapping("/pp/scheduled/{scheduledId}/prescription")
    public com.coachrun.dto.response.StrengthPrescriptionResponse ppPrescription(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID scheduledId) {
        return strengthScheduleService.prescriptionForAthlete(principal.athleteId(), scheduledId);
    }

    /** Suggestion de progression du coach après une séance de force réalisée (§6.7). */
    @GetMapping("/pp/scheduled/{scheduledId}/progression")
    public com.coachrun.dto.response.ProgressionResponse ppProgression(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID scheduledId) {
        return progressionService.forAthlete(principal.athleteId(), scheduledId);
    }

    @PatchMapping("/pp/scheduled/{scheduledId}/move")
    public com.coachrun.dto.response.ScheduledStrengthResponse ppMove(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID scheduledId,
            @Valid @RequestBody com.coachrun.dto.request.WorkoutRescheduleRequest request) {
        return strengthScheduleService.moveByAthlete(principal.athleteId(), scheduledId, request.scheduledDate());
    }

    @PatchMapping("/pp/scheduled/{scheduledId}/feedback")
    public com.coachrun.dto.response.ScheduledStrengthResponse ppFeedback(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID scheduledId,
            @Valid @RequestBody com.coachrun.dto.request.StrengthFeedbackRequest request) {
        return strengthScheduleService.submitFeedback(principal.athleteId(), scheduledId, request);
    }

    /** Séries réalisées d'une séance de force → recalcul automatique du e1RM. */
    @PostMapping("/pp/scheduled/{scheduledId}/results")
    public java.util.List<com.coachrun.dto.response.E1rmHistoryResponse> ppResults(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID scheduledId,
            @Valid @RequestBody java.util.List<com.coachrun.dto.request.StrengthResultRequest> results) {
        return strengthResultService.submit(principal.athleteId(), scheduledId, results);
    }

    /** Mon profil de force : 1RM courant par exercice (lecture seule, CDC §10). */
    @GetMapping("/pp/1rm")
    public java.util.List<com.coachrun.dto.response.MyOneRmResponse> my1rm(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return oneRmService.listForAthlete(principal.athleteId());
    }

    /** Mon historique e1RM d'un exercice (courbe de progression de la force). */
    @GetMapping("/pp/1rm/{exerciseId}/history")
    public java.util.List<com.coachrun.dto.response.E1rmHistoryResponse> my1rmHistory(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID exerciseId) {
        return strengthResultService.historyForAthlete(principal.athleteId(), exerciseId);
    }

    /** Mon profil physiologique (VDOT, LT1/LT2, VC, domaines) — lecture seule. */
    @GetMapping("/physio")
    public com.coachrun.dto.response.PhysioProfileResponse myPhysio(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return physioService.getProfileForAthlete(principal.athleteId());
    }

    /** Mes allures d'entraînement (VDOT). */
    @GetMapping("/vdot")
    public com.coachrun.dto.response.VdotResponse myVdot(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return physioService.getVdotForAthlete(principal.athleteId());
    }

    /** Ma charge d'entraînement (ACWR, ATL/CTL, monotonie, répartition). */
    @GetMapping("/load")
    public com.coachrun.dto.response.LoadResponse myLoad(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return loadService.loadForAthlete(principal.athleteId());
    }

    /** Mes objectifs (liste complète). */
    @GetMapping("/races")
    public java.util.List<com.coachrun.dto.response.RaceObjectiveResponse> myRaces(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return raceService.listForAthlete(principal.athleteId());
    }

    // --- Phase 2 « Mon histoire » (lecture seule) ----------------------------

    /** Mes analytics : volume hebdo prévu/réalisé, répartition de zones, adhérence. */
    @GetMapping("/analytics")
    public com.coachrun.dto.response.AnalyticsResponse myAnalytics(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "8") int weeks) {
        return analyticsService.computeForAthlete(principal.athleteId(), weeks);
    }

    /** Mes activités réalisées (Strava/GPX/manuel), du plus récent au plus ancien. */
    @GetMapping("/activities")
    public java.util.List<com.coachrun.dto.response.ActivityResponse> myActivities(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return activityService.listForAthlete(principal.athleteId());
    }

    /** Tracé GPS d'une de mes activités (carte). */
    @GetMapping("/activities/{activityId}/route")
    public java.util.List<double[]> myActivityRoute(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID activityId) {
        return activityService.routeForAthlete(principal.athleteId(), activityId);
    }

    /** Je consigne une sortie libre (saisie manuelle). */
    @PostMapping("/activities")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public com.coachrun.dto.response.ActivityResponse logActivity(
            @AuthenticationPrincipal AuthPrincipal principal,
            @org.springframework.web.bind.annotation.RequestBody @jakarta.validation.Valid
            com.coachrun.dto.request.ActivityImportRequest request) {
        return activityService.logForAthlete(principal.athleteId(), request);
    }

    /** J'importe ma propre trace (GPX/TCX). */
    @PostMapping("/activities/import-file")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public com.coachrun.dto.response.ActivityResponse importMyFile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @org.springframework.web.bind.annotation.RequestParam("file")
            org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        return activityService.importFileForAthlete(
                principal.athleteId(), file.getOriginalFilename(), file.getBytes());
    }

    /** Mes performances / records (par distance), avec le VDOT impliqué. */
    @GetMapping("/performances")
    public java.util.List<com.coachrun.dto.response.PerformanceResponse> myPerformances(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return physioService.listPerformancesForAthlete(principal.athleteId());
    }

    // --- Phase 3 « Aller plus loin » (lecture seule) -------------------------

    /** Mes tests lactate (résumés : seuils + dates). */
    @GetMapping("/lactate-tests")
    public java.util.List<com.coachrun.dto.response.LactateTestResponse> myLactateTests(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return lactateTestService.listForAthlete(principal.athleteId());
    }

    /** Détail d'un de mes tests lactate (paliers pour la courbe de profil). */
    @GetMapping("/lactate-tests/{testId}")
    public com.coachrun.dto.response.LactateTestResponse myLactateTest(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID testId) {
        return lactateTestService.getForAthlete(principal.athleteId(), testId);
    }

    /** Ma charge de force (méca/métab, UA) par séance réalisée. */
    @GetMapping("/pp/strength-load")
    public java.util.List<com.coachrun.dto.response.StrengthLoadResponse> myStrengthLoad(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return strengthResultService.loadTrackingForAthlete(principal.athleteId());
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

    /** Flux temps réel (SSE) des nouveaux messages pour l'athlète. */
    @GetMapping("/messages/stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter messageStream(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return messageStreamService.subscribe(principal.athleteId());
    }

    /** Envoi d'un message avec pièce jointe (image/PDF). */
    @PostMapping("/messages/attachment")
    @ResponseStatus(HttpStatus.CREATED)
    public com.coachrun.dto.response.MessageResponse sendMessageAttachment(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String body,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return messageService.athleteSendWithAttachment(principal.athleteId(), principal, body, file);
    }

    /** Téléchargement d'une pièce jointe d'un de mes messages. */
    @GetMapping("/messages/{messageId}/attachment")
    public org.springframework.http.ResponseEntity<byte[]> downloadMessageAttachment(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID messageId) {
        com.coachrun.entity.MessageAttachment a =
                messageService.attachmentForAthlete(principal.athleteId(), messageId);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + a.getFilename() + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(a.getContentType()))
                .body(a.getData());
    }

    /** Mes indisponibilités en cours ou à venir. */
    @GetMapping("/unavailabilities")
    public java.util.List<com.coachrun.dto.response.UnavailabilityResponse> unavailabilities(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return unavailabilityService.current(principal.athleteId());
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
