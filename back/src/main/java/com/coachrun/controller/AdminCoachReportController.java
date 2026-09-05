package com.coachrun.controller;

import com.coachrun.dto.request.ClubRequestDecision;
import com.coachrun.dto.response.CoachReportResponse;
import com.coachrun.entity.enums.CoachReportStatus;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.CoachReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * La file des signalements. Réservée à l'administration de la plateforme.
 *
 * <p>Clore un signalement ne touche pas à la fiche : suspendre reste un geste distinct, sur
 * {@code /admin/coach-profiles}. Les enchaîner ici ferait d'un clic de tri une sanction.</p>
 */
@Tag(name = "Admin — Signalements de fiches coachs")
@RestController
@RequestMapping("/admin/coach-reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminCoachReportController {

    private final CoachReportService service;

    @GetMapping
    public List<CoachReportResponse> list(@RequestParam(required = false) CoachReportStatus status) {
        return service.queue(status);
    }

    /** Suite donnée : la fiche a été corrigée ou suspendue. */
    @PostMapping("/{reportId}/act")
    public CoachReportResponse act(@PathVariable UUID reportId,
                                   @Valid @RequestBody(required = false) ClubRequestDecision decision,
                                   @AuthenticationPrincipal AuthPrincipal principal) {
        return service.handle(reportId, CoachReportStatus.ACTED_UPON, principal.userId(),
                decision == null ? null : decision.note());
    }

    /** Sans suite : examiné, il n'y avait rien à corriger. */
    @PostMapping("/{reportId}/dismiss")
    public CoachReportResponse dismiss(@PathVariable UUID reportId,
                                       @Valid @RequestBody(required = false) ClubRequestDecision decision,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        return service.handle(reportId, CoachReportStatus.DISMISSED, principal.userId(),
                decision == null ? null : decision.note());
    }
}
