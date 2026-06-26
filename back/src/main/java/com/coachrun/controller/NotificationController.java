package com.coachrun.controller;

import com.coachrun.dto.response.NotificationResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Centre de notifications de l'utilisateur connecté (coach ou athlète). Scopé par le token :
 * chacun ne voit que ses propres notifications.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final UserNotificationService notificationService;
    private final com.coachrun.service.NotificationStreamService streamService;

    /** Flux temps réel (SSE) du compteur de non-lues. Token via ?access_token= (EventSource). */
    @GetMapping("/stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return streamService.subscribe(principal.userId());
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return notificationService.list(principal.userId(), pageable);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("count", notificationService.unreadCount(principal.userId()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        notificationService.markRead(principal.userId(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AuthPrincipal principal) {
        notificationService.markAllRead(principal.userId());
    }

    @GetMapping("/preferences")
    public com.coachrun.dto.response.NotificationPreferencesResponse preferences(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return notificationService.preferences(principal.userId());
    }

    @org.springframework.web.bind.annotation.PutMapping("/preferences")
    public com.coachrun.dto.response.NotificationPreferencesResponse updatePreferences(
            @AuthenticationPrincipal AuthPrincipal principal,
            @org.springframework.web.bind.annotation.RequestBody
            com.coachrun.dto.request.NotificationPreferencesRequest request) {
        return notificationService.updatePreferences(principal.userId(), request);
    }
}
