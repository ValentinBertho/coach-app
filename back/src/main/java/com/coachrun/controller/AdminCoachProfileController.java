package com.coachrun.controller;

import com.coachrun.dto.request.ClubRequestDecision;
import com.coachrun.dto.response.AdminCoachProfileResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.enums.CoachProfileStatus;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.CoachProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Arbitrage des fiches coachs. Réservé à l'administration de la plateforme.
 *
 * <p>Même forme que l'arbitrage des demandes de création de club, et volontairement : c'est le même
 * geste — une file du matin, une décision, un motif — et l'équipe n'a pas à apprendre deux
 * écrans.</p>
 */
@Tag(name = "Admin — Fiches coachs")
@RestController
@RequestMapping("/admin/coach-profiles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminCoachProfileController {

    private final CoachProfileService service;

    @GetMapping
    public PageResponse<AdminCoachProfileResponse> list(
            @RequestParam(required = false) CoachProfileStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(status, pageable);
    }

    /** Compteur de la pastille « fiches à valider ». */
    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("count", service.countPending());
    }

    @PostMapping("/{profileId}/approve")
    public AdminCoachProfileResponse approve(@PathVariable UUID profileId,
                                             @Valid @RequestBody(required = false) ClubRequestDecision decision,
                                             @AuthenticationPrincipal AuthPrincipal principal) {
        return service.approve(profileId, decision == null ? null : decision.note(), principal);
    }

    @PostMapping("/{profileId}/reject")
    public AdminCoachProfileResponse reject(@PathVariable UUID profileId,
                                            @Valid @RequestBody(required = false) ClubRequestDecision decision,
                                            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.reject(profileId, decision == null ? null : decision.note(), principal);
    }

    @PostMapping("/{profileId}/suspend")
    public AdminCoachProfileResponse suspend(@PathVariable UUID profileId,
                                             @Valid @RequestBody(required = false) ClubRequestDecision decision,
                                             @AuthenticationPrincipal AuthPrincipal principal) {
        return service.suspend(profileId, decision == null ? null : decision.note(), principal);
    }

    /** Lève une suspension : la fiche repasse en validation, pas directement dans l'annuaire. */
    @PostMapping("/{profileId}/reinstate")
    public AdminCoachProfileResponse reinstate(@PathVariable UUID profileId,
                                               @Valid @RequestBody(required = false) ClubRequestDecision decision,
                                               @AuthenticationPrincipal AuthPrincipal principal) {
        return service.reinstate(profileId, decision == null ? null : decision.note(), principal);
    }
}
