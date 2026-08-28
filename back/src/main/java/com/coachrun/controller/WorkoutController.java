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
    private final com.coachrun.service.ActivityService activityService;

    /** Activité réalisée rapprochée de cette séance (vue « réalisé »), ou 204 si aucune. */
    @GetMapping("/{workoutId}/activity")
    public org.springframework.http.ResponseEntity<com.coachrun.dto.response.ActivityResponse> matchedActivity(
            @PathVariable UUID clubId, @PathVariable UUID athleteId, @PathVariable UUID workoutId) {
        var activity = activityService.getForWorkout(clubId, athleteId, workoutId);
        return activity == null
                ? org.springframework.http.ResponseEntity.noContent().build()
                : org.springframework.http.ResponseEntity.ok(activity);
    }

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

    /**
     * Édite la structure (blocs en fourchettes) d'une séance déjà planifiée pour CET athlète :
     * recalcule les cibles et met à jour le snapshot figé. Réservé à l'écriture (canWrite).
     */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PutMapping("/{workoutId}/structure")
    public com.coachrun.dto.response.WorkoutPrescriptionResponse updateStructure(
            @PathVariable UUID clubId, @PathVariable UUID athleteId, @PathVariable UUID workoutId,
            @RequestBody com.coachrun.dto.session.SessionStructure structure) {
        return workoutService.updateStructure(clubId, workoutId, structure);
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

    /**
     * Commentaire du coach sur une séance réalisée (feedback in situ, sans passer par la
     * messagerie). Visible par l'athlète dans son historique et notifié.
     */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canComment(authentication, #athleteId)")
    @PatchMapping("/{workoutId}/coach-comment")
    public WorkoutResponse setCoachComment(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                           @PathVariable UUID workoutId,
                                           @RequestBody java.util.Map<String, String> body) {
        return workoutService.setCoachComment(clubId, workoutId, body.get("comment"));
    }

    /**
     * Renomme la séance. Le geste utile juste après avoir glissé un modèle sur le calendrier :
     * le titre du modèle est le bon défaut, rarement le titre final pour cet athlète-là.
     */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PatchMapping("/{workoutId}/title")
    public WorkoutResponse rename(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                  @PathVariable UUID workoutId,
                                  @Valid @RequestBody com.coachrun.dto.request.WorkoutTitleRequest request) {
        return workoutService.rename(clubId, workoutId, request.title());
    }

    /**
     * Marque le retour de l'athlète comme traité (file « retours à traiter » du tableau de bord).
     * Accusé de lecture côté coach : ne modifie ni la séance ni le retour.
     */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canComment(authentication, #athleteId)")
    @PatchMapping("/{workoutId}/reviewed")
    public WorkoutResponse markReviewed(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                        @PathVariable UUID workoutId,
                                        @RequestParam(defaultValue = "true") boolean reviewed) {
        return workoutService.markFeedbackReviewed(clubId, workoutId, reviewed);
    }

    /**
     * Le « vu 👏 » : traite le retour <b>et</b> le fait savoir à l'athlète.
     *
     * <p>Distinct de {@code /reviewed}, qui reste l'accusé silencieux. Même autorisation : qui
     * peut commenter une séance peut la reconnaître.</p>
     */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canComment(authentication, #athleteId)")
    @PostMapping("/{workoutId}/acknowledge")
    public WorkoutResponse acknowledge(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                       @PathVariable UUID workoutId) {
        return workoutService.acknowledge(clubId, workoutId);
    }

    /** Réordonne les séances d'un même jour (glisser-déposer intra-jour). */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PatchMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                        @Valid @RequestBody com.coachrun.dto.request.WorkoutReorderRequest request) {
        workoutService.reorder(clubId, athleteId, request.date(), request.orderedIds());
    }

    /**
     * Retire l'ordre d'une journée : ses séances redeviennent à faire dans n'importe quel ordre.
     *
     * <p>Le pendant indispensable de {@code /reorder} : sans lui, une journée ordonnée une fois le
     * resterait pour toujours, et un ordre posé par erreur s'afficherait indéfiniment à l'athlète
     * comme une consigne.</p>
     */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @DeleteMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearOrder(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                           @RequestParam @org.springframework.format.annotation.DateTimeFormat(
                                   iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                           java.time.LocalDate date) {
        workoutService.clearOrder(clubId, athleteId, date);
    }

    /** Duplique la séance vers une date (glisser + Alt, ou menu contextuel). */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping("/{workoutId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutResponse copy(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                @PathVariable UUID workoutId,
                                @Valid @RequestBody WorkoutRescheduleRequest request) {
        return workoutService.copyToDate(clubId, workoutId, request.scheduledDate());
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
        int created = workoutService.generateMesocycle(clubId, athleteId, request);
        return java.util.Map.of("created", created);
    }
}
