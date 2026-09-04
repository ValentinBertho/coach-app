package com.coachrun.controller;

import com.coachrun.dto.request.ClubRequestDecision;
import com.coachrun.dto.request.CoachingRequestSubmission;
import com.coachrun.dto.response.CoachingRequestResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.CoachingRequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * Les demandes de coaching, côté athlète.
 *
 * <p>Sous {@code /me/coaching-requests} : elles appartiennent à la <b>personne</b>, pas à une fiche
 * — un athlète qui n'a pas encore de coach n'a pas de fiche, et c'est précisément lui qui demande.</p>
 */
@Tag(name = "Athlète — Mes demandes de coaching")
@RestController
@RequestMapping("/me/coaching-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ATHLETE')")
public class CoachingRequestController {

    private final CoachingRequestService service;

    @GetMapping
    public List<CoachingRequestResponse> mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.myRequests(principal.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CoachingRequestResponse submit(@AuthenticationPrincipal AuthPrincipal principal,
                                          @Valid @RequestBody CoachingRequestSubmission submission,
                                          HttpServletRequest http) {
        return service.submit(principal.userId(), submission,
                http.getRemoteAddr(), http.getHeader("User-Agent"));
    }

    /** L'unique réponse à l'unique question du coach. */
    @PostMapping("/{requestId}/answer")
    public CoachingRequestResponse answer(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID requestId,
                                          @Valid @RequestBody ClubRequestDecision body) {
        return service.answer(principal.userId(), requestId, body == null ? null : body.note());
    }

    /** Retirer sa demande. Retirée n'est pas refusée : l'historique garde la différence. */
    @DeleteMapping("/{requestId}")
    public CoachingRequestResponse withdraw(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable UUID requestId) {
        return service.withdraw(principal.userId(), requestId);
    }
}
