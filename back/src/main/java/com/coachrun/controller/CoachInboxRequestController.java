package com.coachrun.controller;

import com.coachrun.dto.request.ClubRequestDecision;
import com.coachrun.dto.response.CoachingRequestResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.CoachingRequestService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les demandes reçues, côté coach.
 *
 * <p>Sous {@code /me} comme la vitrine : une demande s'adresse à une personne. Un coach qui
 * intervient dans deux clubs reçoit ses demandes au même endroit, et un indépendant n'a pas de club
 * à mettre dans l'adresse.</p>
 */
@Tag(name = "Coach — Demandes reçues")
@RestController
@RequestMapping("/me/received-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COACH','HEAD_COACH')")
public class CoachInboxRequestController {

    private final CoachingRequestService service;

    @GetMapping
    public List<CoachingRequestResponse> received(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.received(principal.userId());
    }

    /** Compteur de la pastille « demandes à traiter ». */
    @GetMapping("/count")
    public Map<String, Long> count(@AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("count", service.pendingCount(principal.userId()));
    }

    /**
     * Accepte : crée la fiche de l'athlète, la relation, et ouvre son espace d'entraînement.
     *
     * <p>Le geste le plus lourd du hub, et le plus irréversible côté athlète : à partir d'ici il a
     * un coach, un calendrier et un fil de discussion.</p>
     */
    @PostMapping("/{requestId}/accept")
    public CoachingRequestResponse accept(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID requestId) {
        return service.accept(principal.userId(), requestId);
    }

    @PostMapping("/{requestId}/decline")
    public CoachingRequestResponse decline(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID requestId,
                                           @Valid @RequestBody(required = false) ClubRequestDecision body) {
        return service.decline(principal.userId(), requestId, body == null ? null : body.note());
    }

    /** L'unique question avant de décider : il n'y a pas de messagerie avant l'accord. */
    @PostMapping("/{requestId}/ask")
    public CoachingRequestResponse ask(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable UUID requestId,
                                       @Valid @RequestBody ClubRequestDecision body) {
        return service.ask(principal.userId(), requestId, body == null ? null : body.note());
    }
}
