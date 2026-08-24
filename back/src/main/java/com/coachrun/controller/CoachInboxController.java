package com.coachrun.controller;

import com.coachrun.dto.response.ConversationSummaryResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.ConversationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Boîte de réception coach : agrégat des conversations tous athlètes confondus. La messagerie
 * n'était accessible qu'athlète par athlète, sans aucune vue des non-lus.
 *
 * <p>Le périmètre est filtré athlète par athlète dans le service — un coach ne voit jamais une
 * conversation hors de son périmètre.</p>
 */
@Tag(name = "Messagerie — Boîte de réception coach")
@RestController
@RequestMapping("/clubs/{clubId}/messages")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class CoachInboxController {

    private final ConversationService conversations;

    @GetMapping("/conversations")
    public List<ConversationSummaryResponse> conversations(@PathVariable UUID clubId,
                                                           @AuthenticationPrincipal AuthPrincipal principal) {
        return conversations.inbox(principal);
    }

    /** Total de non-lus, tous athlètes confondus (badge de la navigation coach). */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@PathVariable UUID clubId,
                                         @AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("count", conversations.unreadCount(principal));
    }
}
