package com.coachrun.controller;

import com.coachrun.dto.response.ProposalResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.CoachDashboardService;
import com.coachrun.service.ProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * La file de propositions du coach : ce que le produit suggère, et les deux seuls gestes possibles
 * dessus — accepter ou refuser.
 *
 * <p>Il n'existe pas de troisième route qui appliquerait une suggestion sans décision : c'est
 * volontaire, et c'est ce qui garantit qu'aucune séance ne change de forme sans qu'un humain l'ait
 * voulu.</p>
 */
@RestController
@RequestMapping("/clubs/{clubId}/proposals")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class ProposalController {

    private final ProposalService proposalService;
    private final CoachDashboardService dashboardService;

    /** Propositions en attente sur le périmètre du coach. */
    @GetMapping
    public List<ProposalResponse> pending(@PathVariable UUID clubId,
                                          @RequestParam(required = false) String scope,
                                          @AuthenticationPrincipal AuthPrincipal principal) {
        return proposalService.pendingFor(
                dashboardService.athletesInScopeFor(clubId, scope, principal.userId()));
    }

    @PostMapping("/{proposalId}/accept")
    public ProposalResponse accept(@PathVariable UUID clubId, @PathVariable UUID proposalId,
                                   @AuthenticationPrincipal AuthPrincipal principal) {
        return proposalService.accept(clubId, proposalId, principal.userId());
    }

    @PostMapping("/{proposalId}/dismiss")
    public ProposalResponse dismiss(@PathVariable UUID clubId, @PathVariable UUID proposalId,
                                    @AuthenticationPrincipal AuthPrincipal principal) {
        return proposalService.dismiss(clubId, proposalId, principal.userId());
    }
}
