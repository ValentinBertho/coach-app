package com.coachrun.controller;

import com.coachrun.dto.response.AnalyticsResponse;
import com.coachrun.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Graphes de charge d'un athlète (agrégés). Scoping tenant. */
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}/analytics")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canRead(authentication, #athleteId)")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public AnalyticsResponse analytics(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                       @RequestParam(defaultValue = "8") int weeks) {
        return analyticsService.compute(clubId, athleteId, weeks);
    }
}
