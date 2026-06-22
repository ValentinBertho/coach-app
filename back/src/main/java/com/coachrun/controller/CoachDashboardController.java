package com.coachrun.controller;

import com.coachrun.dto.response.CoachDashboardResponse;
import com.coachrun.service.CoachDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
