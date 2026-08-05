package com.coachrun.controller;

import com.coachrun.dto.request.ActivityImportRequest;
import com.coachrun.dto.request.ActivityMatchRequest;
import com.coachrun.dto.response.ActivityResponse;
import com.coachrun.dto.response.TimeInZoneResponse;
import com.coachrun.service.ActivityService;
import com.coachrun.service.TimeInZoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Activités réalisées d'un athlète : import (manuel), rapprochement prévu/réalisé.
 * Scoping tenant via @clubAccessValidator.
 */
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}/activities")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canRead(authentication, #athleteId)")
public class ActivityController {

    private final ActivityService activityService;
    private final TimeInZoneService timeInZoneService;

    @GetMapping
    public List<ActivityResponse> list(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return activityService.list(clubId, athleteId);
    }

    /** Temps passé par zone (allure + FC) pour cette activité — barre « zones d'entraînement ». */
    @GetMapping("/{activityId}/time-in-zone")
    public TimeInZoneResponse timeInZone(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                         @PathVariable UUID activityId) {
        return timeInZoneService.forActivity(clubId, activityId);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityResponse importActivity(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                           @Valid @RequestBody ActivityImportRequest request) {
        return activityService.importActivity(clubId, athleteId, request);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping("/import-file")
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityResponse importFile(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                       @org.springframework.web.bind.annotation.RequestParam("file")
                                       org.springframework.web.multipart.MultipartFile file,
                                       @org.springframework.web.bind.annotation.RequestParam(
                                               name = "confirmDuplicate", defaultValue = "false")
                                       boolean confirmDuplicate) throws java.io.IOException {
        return activityService.importFile(clubId, athleteId, file.getOriginalFilename(),
                file.getBytes(), confirmDuplicate);
    }

    @GetMapping("/{activityId}/route")
    public java.util.List<double[]> route(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                          @PathVariable UUID activityId) {
        return activityService.route(clubId, activityId);
    }

    /** Tours de l'activité (montre), ou splits kilométriques calculés à défaut. */
    @GetMapping("/{activityId}/laps")
    public com.coachrun.dto.response.ActivityLapsResponse laps(
            @PathVariable UUID clubId, @PathVariable UUID athleteId, @PathVariable UUID activityId) {
        return activityService.laps(clubId, activityId);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping("/{activityId}/match/{workoutId}")
    public ActivityResponse match(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                  @PathVariable UUID activityId, @PathVariable UUID workoutId) {
        return activityService.matchManually(clubId, activityId, workoutId);
    }

    /**
     * Rapprochement manuel : {@code {"workoutId": …}} pour rattacher, {@code null} pour détacher.
     * Un seul point d'entrée pour les deux sens — et le même côté athlète (/me).
     */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PatchMapping("/{activityId}/match")
    public ActivityResponse patchMatch(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                       @PathVariable UUID activityId,
                                       @RequestBody(required = false) ActivityMatchRequest request) {
        UUID workoutId = request != null ? request.workoutId() : null;
        return workoutId == null
                ? activityService.unmatch(clubId, activityId)
                : activityService.matchManually(clubId, activityId, workoutId);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @DeleteMapping("/{activityId}/match")
    public ActivityResponse unmatch(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                    @PathVariable UUID activityId) {
        return activityService.unmatch(clubId, activityId);
    }
}
