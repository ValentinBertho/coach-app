package com.coachrun.controller;

import com.coachrun.dto.request.ActivityImportRequest;
import com.coachrun.dto.response.ActivityResponse;
import com.coachrun.service.ActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    public List<ActivityResponse> list(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return activityService.list(clubId, athleteId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityResponse importActivity(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                           @Valid @RequestBody ActivityImportRequest request) {
        return activityService.importActivity(clubId, athleteId, request);
    }

    @PostMapping("/{activityId}/match/{workoutId}")
    public ActivityResponse match(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                  @PathVariable UUID activityId, @PathVariable UUID workoutId) {
        return activityService.matchManually(clubId, activityId, workoutId);
    }

    @DeleteMapping("/{activityId}/match")
    public ActivityResponse unmatch(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                    @PathVariable UUID activityId) {
        return activityService.unmatch(clubId, activityId);
    }
}
