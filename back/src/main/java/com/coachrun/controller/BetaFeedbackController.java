package com.coachrun.controller;

import com.coachrun.dto.request.BetaFeedbackRequest;
import com.coachrun.dto.response.BetaFeedbackResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.enums.FeedbackStatus;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.BetaFeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Retours de bêta : dépôt par n'importe quel utilisateur connecté (coach ou athlète), lecture et
 * traitement réservés au {@code PLATFORM_ADMIN}.
 */
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class BetaFeedbackController {

    private final BetaFeedbackService service;

    /**
     * J'envoie un retour. Ouvert à tous les rôles : l'athlète est la moitié des utilisateurs de
     * la bêta et celui qui utilise l'application dans les conditions les moins confortables.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@AuthenticationPrincipal AuthPrincipal principal,
                       @Valid @RequestBody BetaFeedbackRequest request,
                       HttpServletRequest httpRequest) {
        service.submit(principal.userId(), request, httpRequest.getHeader("User-Agent"));
    }

    /** File de traitement (back-office plateforme). */
    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PageResponse<BetaFeedbackResponse> list(
            @RequestParam(required = false) FeedbackStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(status, pageable);
    }

    /** Compteur de retours non lus, pour la pastille du tableau de bord admin. */
    @GetMapping("/count")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Map<String, Long> count() {
        return Map.of("count", service.countNew());
    }

    @PatchMapping("/{feedbackId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(@PathVariable UUID feedbackId, @RequestParam FeedbackStatus status) {
        service.updateStatus(feedbackId, status);
    }
}
