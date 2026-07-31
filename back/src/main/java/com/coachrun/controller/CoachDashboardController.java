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

    /** KPI agrégés, restreints au périmètre {@code scope} (comme /form et /alerts). */
    @GetMapping
    public CoachDashboardResponse dashboard(@PathVariable UUID clubId,
                                            @RequestParam(defaultValue = "all") String scope,
                                            @AuthenticationPrincipal AuthPrincipal principal) {
        return dashboardService.compute(clubId, scope, principal.userId());
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

    /**
     * File « retours à traiter » : séances réalisées avec retour athlète (RPE / douleur /
     * commentaire) non encore marquées comme traitées, tous athlètes du périmètre confondus.
     *
     * @param days profondeur de la fenêtre (14 jours par défaut, 90 au maximum). La file était
     *             sans borne : elle cumulait tout l'historique et devenait illisible.
     */
    @GetMapping("/feedback")
    public java.util.List<com.coachrun.dto.response.FeedbackQueueItemResponse> feedbackQueue(
            @PathVariable UUID clubId,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(defaultValue = "14") int days,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return dashboardService.feedbackQueue(clubId, scope, principal.userId(), days);
    }

    /**
     * Marque comme traités tous les retours du périmètre et de la fenêtre courants. Vider la
     * file demandait sinon un clic par ligne.
     *
     * @return {@code {"marked": n}}
     */
    @org.springframework.web.bind.annotation.PostMapping("/feedback/review-all")
    public java.util.Map<String, Integer> markAllFeedbackReviewed(
            @PathVariable UUID clubId,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(defaultValue = "14") int days,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return java.util.Map.of("marked",
                dashboardService.markAllFeedbackReviewed(clubId, scope, principal.userId(), days));
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
