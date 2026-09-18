package com.coachrun.controller;

import com.coachrun.dto.request.AdHocStrengthRequest;
import com.coachrun.dto.request.SaveAsStrengthSessionRequest;
import com.coachrun.dto.request.ScheduleStrengthRequest;
import com.coachrun.dto.request.StrengthCopyFromRequest;
import com.coachrun.dto.request.StrengthStructureRequest;
import com.coachrun.dto.request.WorkoutRescheduleRequest;
import com.coachrun.dto.request.WorkoutTitleRequest;
import com.coachrun.dto.response.ScheduledStrengthResponse;
import com.coachrun.dto.response.StrengthPrescriptionResponse;
import com.coachrun.dto.response.StrengthSessionResponse;
import com.coachrun.service.StrengthScheduleService;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/** Calendrier de force d'un athlète, côté coach (cf. DARI Lab). Scoping tenant. */
@Tag(name = "Préparation physique — Calendrier")
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}/pp")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canRead(authentication, #athleteId)")
public class StrengthScheduleController {

    private final StrengthScheduleService scheduleService;

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping("/sessions/{sessionId}/schedule")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduledStrengthResponse schedule(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                              @PathVariable UUID sessionId,
                                              @Valid @RequestBody ScheduleStrengthRequest request) {
        return scheduleService.schedule(clubId, athleteId, sessionId, request.date(), request.fieldsPreset());
    }

    @GetMapping("/scheduled")
    public List<ScheduledStrengthResponse> calendar(
            @PathVariable UUID clubId, @PathVariable UUID athleteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleService.coachCalendar(clubId, athleteId, from, to);
    }

    @GetMapping("/scheduled/{scheduledId}/prescription")
    public StrengthPrescriptionResponse prescription(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                                     @PathVariable UUID scheduledId) {
        return scheduleService.prescription(clubId, scheduledId);
    }

    /** Déplacement d'une séance de force par le coach (glisser-déposer du calendrier). */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PatchMapping("/scheduled/{scheduledId}/reschedule")
    public ScheduledStrengthResponse reschedule(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                                @PathVariable UUID scheduledId,
                                                @Valid @RequestBody WorkoutRescheduleRequest request) {
        return scheduleService.moveByCoach(clubId, athleteId, scheduledId, request.scheduledDate());
    }

    /** Marque le retour de la séance de force comme traité (file « retours à traiter »). */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canComment(authentication, #athleteId)")
    @PatchMapping("/scheduled/{scheduledId}/reviewed")
    public ScheduledStrengthResponse markReviewed(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                                  @PathVariable UUID scheduledId,
                                                  @RequestParam(defaultValue = "true") boolean reviewed) {
        return scheduleService.markFeedbackReviewed(clubId, athleteId, scheduledId, reviewed);
    }

    /** Le « vu 👏 » : traite le débrief de force <b>et</b> le fait savoir à l'athlète. */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canComment(authentication, #athleteId)")
    @PostMapping("/scheduled/{scheduledId}/acknowledge")
    public ScheduledStrengthResponse acknowledge(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                                 @PathVariable UUID scheduledId) {
        return scheduleService.acknowledge(clubId, athleteId, scheduledId);
    }

    /**
     * Pose une séance de renforcement vierge sur un jour, sans passer par la bibliothèque.
     *
     * <p>Le chemin court que la course avait déjà : on pose la séance là où elle doit avoir lieu,
     * puis on la remplit dans l'éditeur. Construire un modèle pour une séance qui ne resservira
     * pas était le détour le plus coûteux de la prépa physique.</p>
     */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping("/scheduled")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduledStrengthResponse createAdHoc(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                                 @Valid @RequestBody AdHocStrengthRequest request) {
        return scheduleService.createAdHoc(clubId, athleteId, request.date(), request.title(),
                request.fieldsPreset());
    }

    /**
     * Réécrit le contenu d'une séance de renforcement planifiée — sans créer de nouvelle séance
     * ni toucher au modèle de bibliothèque. Pendant force de l'édition de structure course.
     */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PutMapping("/scheduled/{scheduledId}/structure")
    public StrengthPrescriptionResponse updateStructure(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                                        @PathVariable UUID scheduledId,
                                                        @RequestBody StrengthStructureRequest request) {
        return scheduleService.updateStructure(clubId, athleteId, scheduledId, request.structure());
    }

    /** Renomme une séance de force planifiée, sans relire ni réécrire sa prescription figée. */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PatchMapping("/scheduled/{scheduledId}/title")
    public ScheduledStrengthResponse rename(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                            @PathVariable UUID scheduledId,
                                            @Valid @RequestBody WorkoutTitleRequest request) {
        return scheduleService.rename(clubId, athleteId, scheduledId, request.title());
    }

    /**
     * Duplique la séance vers une date (copier-coller du calendrier, glisser + Alt).
     *
     * <p>C'est la séance <b>affichée</b> qui est recopiée, snapshot compris : le collage repassait
     * par le modèle de bibliothèque, et rendait donc sa version d'origine — ou échouait faute de
     * modèle.</p>
     */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping("/scheduled/{scheduledId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduledStrengthResponse copy(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                          @PathVariable UUID scheduledId,
                                          @Valid @RequestBody WorkoutRescheduleRequest request) {
        return scheduleService.copyToDate(clubId, athleteId, scheduledId, request.scheduledDate());
    }

    /**
     * Copie ici la séance de renforcement d'un autre athlète du club (copier-coller de la vue
     * groupe). L'athlète de l'URL est la <b>cible</b> ; les charges sont recalculées pour lui.
     */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping("/scheduled/copy-from")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduledStrengthResponse copyFrom(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                              @Valid @RequestBody StrengthCopyFromRequest request) {
        return scheduleService.copyToAthlete(clubId, athleteId, request.sourceScheduledId(),
                request.scheduledDate());
    }

    /** Verse dans la bibliothèque une séance de renforcement construite au calendrier. */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canRead(authentication, #athleteId)")
    @PostMapping("/scheduled/{scheduledId}/save-as-session")
    @ResponseStatus(HttpStatus.CREATED)
    public StrengthSessionResponse saveAsSession(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                                 @PathVariable UUID scheduledId,
                                                 @Valid @RequestBody SaveAsStrengthSessionRequest request) {
        return scheduleService.saveAsLibrarySession(clubId, athleteId, scheduledId,
                request.name(), request.notes(), request.categoryId());
    }

    /** Déprogrammation d'une séance de force depuis le calendrier coach. */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @DeleteMapping("/scheduled/{scheduledId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                       @PathVariable UUID scheduledId) {
        scheduleService.delete(clubId, athleteId, scheduledId);
    }
}
