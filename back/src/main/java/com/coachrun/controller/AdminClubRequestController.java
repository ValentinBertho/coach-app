package com.coachrun.controller;

import com.coachrun.dto.request.ClubRequestDecision;
import com.coachrun.dto.response.ClubCreationRequestResponse;
import com.coachrun.dto.response.ClubRequestApprovalResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.enums.ClubRequestStatus;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.ClubCreationRequestService;
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
 * Arbitrage des demandes de création de club. Réservé à l'administration de la plateforme.
 *
 * <p>C'est la contrepartie du formulaire public : sans écran d'arbitrage, le régime « sur
 * demande » n'en serait pas un — les demandes tomberaient dans une table que personne n'ouvre, et
 * les candidats attendraient une réponse qui ne viendrait jamais.</p>
 */
@Tag(name = "Admin — Demandes de création de club")
@RestController
@RequestMapping("/admin/club-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminClubRequestController {

    private final ClubCreationRequestService service;

    /** File d'arbitrage. Sans filtre, tout l'historique, du plus récent au plus ancien. */
    @GetMapping
    public PageResponse<ClubCreationRequestResponse> list(
            @RequestParam(required = false) ClubRequestStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(status, pageable);
    }

    /** Compteur de la pastille « demandes à étudier ». */
    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("count", service.countPending());
    }

    /**
     * Valide la demande : ouvre le club, crée le compte du coach, lui envoie son lien d'entrée.
     *
     * <p>La réponse porte ce lien. L'envoi d'e-mails peut être éteint, ou l'adresse rebondir :
     * sans le lien sous la main, l'administrateur n'aurait aucun moyen de débloquer le coach
     * qu'il vient d'accepter.</p>
     */
    @PostMapping("/{requestId}/approve")
    public ClubRequestApprovalResponse approve(@PathVariable UUID requestId,
                                               @Valid @RequestBody(required = false)
                                               ClubRequestDecision decision,
                                               @AuthenticationPrincipal AuthPrincipal principal) {
        return service.approve(requestId, decision, principal);
    }

    /** Refuse la demande. Le motif, quand il est renseigné, part au demandeur. */
    @PostMapping("/{requestId}/reject")
    public ClubCreationRequestResponse reject(@PathVariable UUID requestId,
                                              @Valid @RequestBody(required = false)
                                              ClubRequestDecision decision,
                                              @AuthenticationPrincipal AuthPrincipal principal) {
        return service.reject(requestId, decision, principal);
    }
}
