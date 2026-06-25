package com.coachrun.controller;

import com.coachrun.dto.response.CoachDashboardResponse;
import com.coachrun.dto.response.CoachFormDashboardResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.CoachDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Tableau de bord coach (indicateurs agrégés). Scoping tenant. */
@RestController
@RequestMapping("/clubs/{clubId}/dashboard")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class CoachDashboardController {

    private final CoachDashboardService dashboardService;

    @GetMapping
    public CoachDashboardResponse dashboard(@PathVariable UUID clubId) {
        return dashboardService.compute(clubId);
    }

    /**
     * Tableau de bord « état de forme » : athlètes Route/Trail avec leur pastille de forme.
     * Périmètre {@code scope} : all (tout le club) | mine (mes athlètes) | private (mes privés)
     * | club (mes athlètes club).
     */
    @GetMapping("/form")
    public CoachFormDashboardResponse form(@PathVariable UUID clubId,
                                           @RequestParam(defaultValue = "all") String scope,
                                           @AuthenticationPrincipal AuthPrincipal principal) {
        return dashboardService.formDashboard(clubId, scope, principal.userId());
    }

    /** File d'alertes actionnables (douleur, charge, séances manquées, silence), triées par gravité. */
    @GetMapping("/alerts")
    public java.util.List<com.coachrun.dto.response.CoachAlertResponse> alerts(
            @PathVariable UUID clubId,
            @RequestParam(defaultValue = "all") String scope,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return dashboardService.alerts(clubId, scope, principal.userId());
    }
}
