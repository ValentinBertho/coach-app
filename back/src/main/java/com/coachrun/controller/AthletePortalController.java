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
    private final com.coachrun.service.ConversationService conversationService;
    private final com.coachrun.service.StrengthScheduleService strengthScheduleService;
    private final com.coachrun.service.StrengthResultService strengthResultService;
    private final com.coachrun.service.ProgressionService progressionService;
    private final com.coachrun.service.UnavailabilityService unavailabilityService;
    private final com.coachrun.service.Athlete1rmService oneRmService;
    private final com.coachrun.service.AthletePhysioService physioService;
    private final com.coachrun.service.AthleteLoadService loadService;
    private final com.coachrun.service.AnalyticsService analyticsService;
    private final com.coachrun.service.ActivityService activityService;
    private final com.coachrun.service.CalendarNoteService calendarNoteService;
    private final com.coachrun.service.LactateTestService lactateTestService;
    private final com.coachrun.service.TrainingPlanService trainingPlanService;
    private final com.coachrun.service.StravaService stravaService;
    private final com.coachrun.service.ProposalService proposalService;
    private final com.coachrun.service.ReadinessService readinessService;
    private final com.coachrun.service.ComplianceService complianceService;
    private final com.coachrun.service.TrajectoryService trajectoryService;
    private final com.coachrun.service.WeekOutlookService weekOutlookService;
    private final com.coachrun.service.AthleteTimelineService timelineService;
    private final com.coachrun.service.DailyCheckInService checkInService;
    private final com.coachrun.service.ClockService clock;
    private final com.coachrun.service.FeedbackStreakService streakService;
    private final com.coachrun.service.TimeInZoneService timeInZoneService;

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
        LocalDate day = date != null ? date : clock.today();
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
        return workoutService.submitFeedback(principal.athleteId(), workoutId, request);
    }

    /**
     * Série de retours consécutifs — alimente la célébration affichée à la validation.
     * Volontairement à part de la réponse de feedback : c'est de l'encouragement, pas de la
     * donnée d'entraînement, et un échec de calcul ne doit jamais faire échouer un ressenti.
     */
    @GetMapping("/feedback-streak")
    public java.util.Map<String, Integer> feedbackStreak(@AuthenticationPrincipal AuthPrincipal principal) {
        return java.util.Map.of("streak", streakService.currentStreak(principal.athleteId()));
    }

    /**
     * Séance rapprochée d'une activité importée dont le ressenti manque encore : le portail
     * ouvre la feuille pré-remplie à la prochaine ouverture. 204 quand il n'y a rien.
     */
    @GetMapping("/feedback-prompt")
    public org.springframework.http.ResponseEntity<com.coachrun.dto.response.FeedbackPromptResponse>
            feedbackPrompt(@AuthenticationPrincipal AuthPrincipal principal) {
        var prompt = activityService.pendingFeedbackPrompt(principal.athleteId());
        return prompt == null
                ? org.springframework.http.ResponseEntity.noContent().build()
                : org.springframework.http.ResponseEntity.ok(prompt);
    }

    /** L'invitation a été vue (remplie ou écartée) : ne plus la reproposer. */
    @PostMapping("/feedback-prompt/{activityId}/ack")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ackFeedbackPrompt(@AuthenticationPrincipal AuthPrincipal principal,
                                  @PathVariable UUID activityId) {
        activityService.ackFeedbackPrompt(principal.athleteId(), activityId);
    }

    // --- Check-in matinal (3 curseurs) ---------------------------------------

    /**
     * Mon check-in du jour (204 s'il n'est pas rempli). Fatigue et douleur alimentent la forme
     * <strong>avant</strong> la séance ; le sommeil reste du contexte.
     */
    @GetMapping("/checkin")
    public org.springframework.http.ResponseEntity<com.coachrun.dto.response.DailyCheckInResponse> checkIn(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        var checkIn = checkInService.forDay(principal.athleteId(), date != null ? date : clock.today());
        return checkIn == null
                ? org.springframework.http.ResponseEntity.noContent().build()
                : org.springframework.http.ResponseEntity.ok(checkIn);
    }

    /** Je déclare (ou corrige) mon check-in du jour. */
    @PostMapping("/checkin")
    public com.coachrun.dto.response.DailyCheckInResponse saveCheckIn(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody com.coachrun.dto.request.DailyCheckInRequest request) {
        return checkInService.save(principal.athleteId(), clock.today(), request);
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

    /**
     * Mes cycles d'entraînement sur une fenêtre : le bloc dans lequel je me situe.
     *
     * <p>Le coach pose ses cycles sur le calendrier — « spécifique », « affûtage » — et ils ne se
     * voyaient que de son côté. L'athlète suivait donc son programme sans savoir à quelle phase de
     * sa préparation il appartenait, alors que c'est ce qui donne son sens à une semaine.</p>
     *
     * <p>Seuls les <b>cycles</b> remontent : les notes d'un jour restent le carnet de travail du
     * coach (cf. {@code CalendarNoteService#cyclesForAthlete}).</p>
     */
    @GetMapping("/cycles")
    public java.util.List<com.coachrun.dto.response.CalendarNoteResponse> myCycles(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return calendarNoteService.cyclesForAthlete(principal.athleteId(), from, to);
    }

    /** Une de mes séances, en détail (fiche « séance réalisée »). */
    @GetMapping("/workouts/{workoutId}")
    public WorkoutResponse myWorkout(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable UUID workoutId) {
        return workoutService.getForAthlete(principal.athleteId(), workoutId);
    }

    /**
     * Marque comme lu le mot du coach sur cette séance.
     *
     * <p>Appelé à l'ouverture de la fiche : le commentaire cesse alors de remonter sur
     * « Aujourd'hui ». Idempotent — la date de première lecture ne bouge plus, sinon « lu il y a
     * trois jours » deviendrait « lu à l'instant » à chaque consultation.</p>
     */
    @PostMapping("/workouts/{workoutId}/coach-comment/read")
    public WorkoutResponse readCoachComment(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable UUID workoutId) {
        return workoutService.markCoachCommentRead(principal.athleteId(), workoutId);
    }

    /**
     * Les mots du coach que je n'ai pas encore lus, séance la plus récente d'abord.
     *
     * <p>Sert la carte d'« Aujourd'hui ». Sans elle, un commentaire posé sur une sortie de
     * l'avant-veille n'apparaissait nulle part : ni sur la journée du jour, qui ne connaît que
     * la séance du jour, ni dans le calendrier, qui n'affiche pas les commentaires.</p>
     */
    @GetMapping("/coach-comments/unread")
    public java.util.List<WorkoutResponse> unreadCoachComments(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return workoutService.unreadCoachComments(principal.athleteId());
    }

    /** Ma sortie rapprochée de cette séance (vue « réalisé »), ou 204 si aucune. */
    @GetMapping("/workouts/{workoutId}/activity")
    public org.springframework.http.ResponseEntity<com.coachrun.dto.response.ActivityResponse> myWorkoutActivity(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID workoutId) {
        var activity = activityService.getForWorkoutOfAthlete(principal.athleteId(), workoutId);
        return activity == null
                ? org.springframework.http.ResponseEntity.noContent().build()
                : org.springframework.http.ResponseEntity.ok(activity);
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

    /** Je crée un objectif de course. */
    @PostMapping("/races")
    @ResponseStatus(HttpStatus.CREATED)
    public com.coachrun.dto.response.RaceObjectiveResponse createRace(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody com.coachrun.dto.request.RaceObjectiveRequest request) {
        return raceService.createForAthlete(principal.athleteId(), request);
    }

    /** Je modifie un de mes objectifs. */
    @PatchMapping("/races/{raceId}")
    public com.coachrun.dto.response.RaceObjectiveResponse updateRace(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID raceId,
            @Valid @RequestBody com.coachrun.dto.request.RaceObjectiveRequest request) {
        return raceService.updateForAthlete(principal.athleteId(), raceId, request);
    }

    /** Je supprime un de mes objectifs. */
    @DeleteMapping("/races/{raceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRace(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID raceId) {
        raceService.deleteForAthlete(principal.athleteId(), raceId);
    }

    // --- Phase 2 « Mon histoire » (lecture seule) ----------------------------

    /** Mes analytics : volume hebdo prévu/réalisé, répartition de zones, adhérence. */
    @GetMapping("/analytics")
    public com.coachrun.dto.response.AnalyticsResponse myAnalytics(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "8") int weeks) {
        return analyticsService.computeForAthlete(principal.athleteId(), weeks);
    }

    /** Ma semaine en un chiffre : « 32/45 km, 3 séances sur 5 ». */
    @GetMapping("/week-summary")
    public com.coachrun.dto.response.WeekSummaryResponse myWeekSummary(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return analyticsService.weekSummary(principal.athleteId());
    }

    /** Temps passé par zone pour une de mes activités (l'endpoint coach existait déjà). */
    @GetMapping("/activities/{activityId}/time-in-zone")
    public com.coachrun.dto.response.TimeInZoneResponse myActivityTimeInZone(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID activityId) {
        return timeInZoneService.forActivityOfAthlete(principal.athleteId(), activityId);
    }

    /**
     * Je corrige une de mes sorties, et j'y laisse mon ressenti pour mon coach.
     *
     * <p>Indispensable sur une sortie <b>non prescrite</b> : le RPE et le commentaire vivaient
     * uniquement sur les séances du programme, donc une course ou un footing improvisé
     * arrivaient chez le coach sans un mot, avec les seuls chiffres de la montre.</p>
     */
    @PatchMapping("/activities/{activityId}")
    public com.coachrun.dto.response.ActivityResponse updateMyActivity(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID activityId,
            @Valid @RequestBody com.coachrun.dto.request.ActivityUpdateRequest request) {
        return activityService.updateForAthlete(principal.athleteId(), activityId, request);
    }

    /** Je supprime une de mes sorties (doublon, erreur de saisie). */
    @DeleteMapping("/activities/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyActivity(@AuthenticationPrincipal AuthPrincipal principal,
                                 @PathVariable UUID activityId) {
        activityService.deleteForAthlete(principal.athleteId(), activityId);
    }

    /**
     * Tours d'une de mes sorties : les répétitions telles que ma montre les a découpées, ou des
     * splits kilométriques calculés si elle ne l'a pas fait. C'est ce qui permet de relire un
     * fractionné autrement que par sa moyenne.
     */
    @GetMapping("/activities/{activityId}/laps")
    public com.coachrun.dto.response.ActivityLapsResponse myActivityLaps(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID activityId,
            @RequestParam(required = false) com.coachrun.dto.response.ActivityLapsResponse.Kind kind) {
        return activityService.lapsForAthlete(principal.athleteId(), activityId, kind);
    }

    /**
     * Courbe d'une de mes sorties : FC et allure le long de la distance.
     *
     * <p>C'est la lecture qui manquait le plus au portail — les moyennes disent ce que la séance
     * a coûté, la courbe dit comment elle s'est passée.</p>
     */
    @GetMapping("/activities/{activityId}/stream")
    public com.coachrun.dto.response.ActivityStreamResponse myActivityStream(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID activityId) {
        return activityService.streamForAthlete(principal.athleteId(), activityId);
    }

    /** Mes activités réalisées (Strava/GPX/manuel), du plus récent au plus ancien. */
    @GetMapping("/activities")
    public java.util.List<com.coachrun.dto.response.ActivityResponse> myActivities(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return activityService.listForAthlete(principal.athleteId());
    }

    /**
     * Je rattache (ou détache) une de mes sorties à une séance prescrite. L'algorithme se trompe
     * — deux sorties le même jour, une séance déplacée — et son erreur était sans recours.
     */
    @PatchMapping("/activities/{activityId}/match")
    public com.coachrun.dto.response.ActivityResponse matchMyActivity(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID activityId,
            @RequestBody(required = false) com.coachrun.dto.request.ActivityMatchRequest request) {
        return activityService.matchForAthlete(principal.athleteId(), activityId,
                request != null ? request.workoutId() : null);
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
            org.springframework.web.multipart.MultipartFile file,
            @org.springframework.web.bind.annotation.RequestParam(
                    name = "confirmDuplicate", defaultValue = "false")
            boolean confirmDuplicate) throws java.io.IOException {
        return activityService.importFileForAthlete(
                principal.athleteId(), file.getOriginalFilename(), file.getBytes(), confirmDuplicate);
    }

    /** Mes performances / records (par distance), avec le VDOT impliqué. */
    @GetMapping("/performances")
    public java.util.List<com.coachrun.dto.response.PerformanceResponse> myPerformances(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return physioService.listPerformancesForAthlete(principal.athleteId());
    }

    /** Je déclare une performance de référence (chrono sur une distance) → recalcul VDOT + allures. */
    @PostMapping("/performances")
    @ResponseStatus(HttpStatus.CREATED)
    public com.coachrun.dto.response.PerformanceResponse addPerformance(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody com.coachrun.dto.request.PerformanceRequest request) {
        return physioService.addPerformanceForAthlete(principal.athleteId(), request);
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

    /**
     * Notes du calendrier visibles par l'athlète : les cycles, les mots que son coach lui a
     * adressés, et les siens.
     */
    @GetMapping("/calendar-notes")
    public java.util.List<com.coachrun.dto.response.CalendarNoteResponse> calendarNotes(
            @AuthenticationPrincipal AuthPrincipal principal,
            @org.springframework.web.bind.annotation.RequestParam
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate from,
            @org.springframework.web.bind.annotation.RequestParam
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate to) {
        return calendarNoteService.listForAthlete(principal.athleteId(), from, to);
    }

    /**
     * L'athlète pose un mot sur une journée : « je finis tard mardi », « piste fermée jeudi ».
     *
     * <p>Ce n'est ni une séance ni une indisponibilité — c'est le fait simple qu'on signale, et
     * qui n'avait jusqu'ici aucun endroit où s'écrire.</p>
     */
    @PostMapping("/calendar-notes")
    @ResponseStatus(HttpStatus.CREATED)
    public com.coachrun.dto.response.CalendarNoteResponse addCalendarNote(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody com.coachrun.dto.request.CalendarNoteRequest request) {
        return calendarNoteService.createByAthlete(principal.athleteId(), principal.userId(), request);
    }

    /** Retirer SON mot — pas celui du coach. */
    @org.springframework.web.bind.annotation.DeleteMapping("/calendar-notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCalendarNote(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable UUID noteId) {
        calendarNoteService.deleteByAthlete(principal.athleteId(), noteId, principal.userId());
    }

    /** Messagerie : fil de discussion avec le coach. */
    @GetMapping("/messages")
    public java.util.List<com.coachrun.dto.response.MessageResponse> messages(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "100") int limit) {
        return messageService.athleteThread(principal, limit);
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public com.coachrun.dto.response.MessageResponse sendMessage(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody com.coachrun.dto.request.MessageRequest request) {
        return messageService.athleteSend(principal.athleteId(), principal, request);
    }

    /**
     * Flux temps réel (SSE) du fil courant de l'athlète.
     *
     * <p>Le flux est indexé par <b>conversation</b> depuis le cloisonnement de la messagerie : sans
     * cette résolution, cette route écouterait une clé sur laquelle plus personne ne diffuse — un
     * flux ouvert, silencieux, et impossible à distinguer d'un fil calme. L'écran de messagerie,
     * lui, s'abonne explicitement au fil qu'il affiche.</p>
     */
    @GetMapping("/messages/stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter messageStream(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return conversationService.defaultAthleteConversation(principal.athleteId())
                .map(c -> messageStreamService.subscribe(c.getId()))
                .orElseGet(() -> messageStreamService.subscribe(principal.athleteId()));
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

    // --- Synchronisation Strava (côté athlète : je connecte MA propre montre, CDC §12) ---

    /** Statut de ma connexion Strava. */
    @GetMapping("/strava")
    public com.coachrun.dto.response.StravaStatusResponse stravaStatus(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return stravaService.statusForAthlete(principal.athleteId());
    }

    /** URL d'autorisation Strava à ouvrir dans le navigateur. */
    @GetMapping("/strava/authorize")
    public java.util.Map<String, String> stravaAuthorize(@AuthenticationPrincipal AuthPrincipal principal) {
        return java.util.Map.of("url", stravaService.authorizeUrlForAthlete(principal.athleteId()));
    }

    /** Finalise ma connexion Strava avec le code d'autorisation et le state signé. */
    @PostMapping("/strava/connect")
    public com.coachrun.dto.response.StravaStatusResponse stravaConnect(
            @AuthenticationPrincipal AuthPrincipal principal, @RequestBody java.util.Map<String, String> body) {
        return stravaService.connectForAthlete(principal.athleteId(), body.get("code"), body.get("state"));
    }

    /** J'importe maintenant mes nouvelles activités Strava. */
    @PostMapping("/strava/import")
    public java.util.Map<String, Integer> stravaImport(@AuthenticationPrincipal AuthPrincipal principal) {
        return java.util.Map.of("imported", stravaService.importForAthlete(principal.athleteId()));
    }

    /** Je déconnecte mon compte Strava. */
    @DeleteMapping("/strava")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stravaDisconnect(@AuthenticationPrincipal AuthPrincipal principal) {
        stravaService.disconnectForAthlete(principal.athleteId());
    }

    /** Mes indisponibilités en cours ou à venir. */
    @GetMapping("/unavailabilities")
    public java.util.List<com.coachrun.dto.response.UnavailabilityResponse> unavailabilities(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return unavailabilityService.current(principal.athleteId());
    }

    /** Je déclare une indisponibilité (blessure, maladie, vacances…) — mon coach est prévenu. */
    @PostMapping("/unavailabilities")
    @ResponseStatus(HttpStatus.CREATED)
    public com.coachrun.dto.response.UnavailabilityResponse declareUnavailability(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody com.coachrun.dto.request.UnavailabilityRequest request) {
        return unavailabilityService.createForAthlete(principal.athleteId(), request);
    }

    /** Je retire une indisponibilité que j'ai déclarée. */
    @DeleteMapping("/unavailabilities/{unavailabilityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeUnavailability(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable UUID unavailabilityId) {
        unavailabilityService.deleteForAthlete(principal.athleteId(), unavailabilityId);
    }

    /** RGPD — portabilité : export des données personnelles de l'athlète. */
    @GetMapping("/export")
    public AthleteExportResponse export(@AuthenticationPrincipal AuthPrincipal principal) {
        return gdprService.export(principal.athleteId());
    }

    /** RGPD — état de mon consentement au traitement des données de santé. */
    @GetMapping("/consent")
    public com.coachrun.dto.response.HealthConsentResponse consent(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return gdprService.consent(principal.athleteId());
    }

    /**
     * RGPD art. 7-3 — retrait du consentement au traitement des données de santé.
     *
     * <p>La politique de confidentialité promet ce retrait « à tout moment » ; il n'existait
     * nulle part. Le consentement était écrit une fois à l'acceptation de l'invitation, puis
     * jamais relu ailleurs que dans l'export : c'était une case franchie, pas un état gouvernant
     * le traitement. L'article 7-3 exige qu'il soit aussi simple de retirer que de donner.</p>
     *
     * <p>Le retrait n'efface pas le compte — c'est le rôle du droit à l'oubli, distinct. Il
     * efface les données de santé déjà collectées, coupe leur collecte future, et prévient le
     * coach référent : sans lui, le coach continuerait de saisir des mesures qui seraient
     * désormais refusées, sans comprendre pourquoi.</p>
     */
    @org.springframework.web.bind.annotation.PostMapping("/consent/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdrawConsent(@AuthenticationPrincipal AuthPrincipal principal) {
        gdprService.withdrawHealthConsent(principal.athleteId());
    }

    /** RGPD — je redonne mon consentement après l'avoir retiré. */
    @org.springframework.web.bind.annotation.PostMapping("/consent/grant")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantConsent(@AuthenticationPrincipal AuthPrincipal principal) {
        gdprService.grantHealthConsent(principal.athleteId());
    }

    /** RGPD — droit à l'oubli : suppression du compte et de toutes les données. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal AuthPrincipal principal) {
        gdprService.deleteAthleteData(principal.athleteId());
    }

    // --- Ce que le produit conclut, pour moi ---------------------------------

    /**
     * Le feu vert du matin : ce que le produit conseille de faire de ma séance du jour.
     *
     * <p>Lecture pure. Rien n'est modifié ici, et rien ne le sera sans que je le demande
     * explicitement — puis que mon coach l'accepte.</p>
     */
    @GetMapping("/readiness")
    public com.coachrun.dto.response.ReadinessResponse readiness(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return readinessService.forAthlete(principal.athleteId());
    }

    /**
     * Je demande l'adaptation conseillée. Cela crée une <b>demande</b> adressée à mon coach ;
     * ma séance ne change pas tant qu'il ne l'a pas acceptée.
     */
    @PostMapping("/readiness/request")
    public com.coachrun.dto.response.ReadinessResponse requestAdaptation(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) com.coachrun.engine.SessionAdaptationEngine.Advice advice) {
        return readinessService.request(principal.athleteId(), advice);
    }

    /** Les demandes et suggestions me concernant, encore en attente de décision. */
    @GetMapping("/proposals")
    public List<com.coachrun.dto.response.ProposalResponse> myProposals(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return proposalService.pendingForAthlete(principal.athleteId());
    }

    /** « Séance tenue ? » — le verdict d'une de mes séances, bloc par bloc. */
    @GetMapping("/workouts/{workoutId}/compliance")
    public com.coachrun.dto.response.ComplianceResponse compliance(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID workoutId) {
        return complianceService.forAthlete(principal.athleteId(), workoutId);
    }

    /** Ma trajectoire vers ma prochaine course (204 si je n'ai pas d'objectif). */
    @GetMapping("/trajectory")
    public org.springframework.http.ResponseEntity<com.coachrun.dto.response.TrajectoryResponse> trajectory(
            @AuthenticationPrincipal AuthPrincipal principal) {
        var t = trajectoryService.forAthlete(principal.athleteId());
        return t == null
                ? org.springframework.http.ResponseEntity.noContent().build()
                : org.springframework.http.ResponseEntity.ok(t);
    }

    /** Ma semaine : chargée, normale, ou de décharge. */
    @GetMapping("/week-outlook")
    public com.coachrun.dto.response.WeekOutlookResponse weekOutlook(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        return weekOutlookService.forMyWeek(principal.athleteId(), week);
    }

    /** Mon fil : tests, records, courses, coupures. */
    @GetMapping("/timeline")
    public com.coachrun.dto.response.TimelineResponse timeline(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return timelineService.forAthlete(principal.athleteId(), from, to);
    }
}
