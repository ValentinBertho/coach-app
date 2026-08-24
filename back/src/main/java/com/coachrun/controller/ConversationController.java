package com.coachrun.controller;

import com.coachrun.dto.request.MessageRequest;
import com.coachrun.dto.response.ConversationSummaryResponse;
import com.coachrun.dto.response.MessageResponse;
import com.coachrun.dto.response.RecipientResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.ConversationService;
import com.coachrun.service.MessageService;
import com.coachrun.service.MessageStreamService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Messagerie : une seule API pour les deux rôles.
 *
 * <p>Coach et athlète avaient chacun leur route ({@code /clubs/…/athletes/…/messages} d'un côté,
 * {@code /me/messages} de l'autre), ce qui obligeait à écrire deux fois toute règle de
 * cloisonnement. Ici, un fil est un fil : l'appartenance décide de ce qu'on en voit, quel que soit
 * le rôle de qui demande. Les anciennes routes subsistent et retombent sur ce même modèle.</p>
 */
@Tag(name = "Messagerie — conversations")
@RestController
@RequestMapping("/me/conversations")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ConversationController {

    private final ConversationService conversations;
    private final MessageService messageService;
    private final MessageStreamService streamService;

    /** Mes fils, du plus récent au plus ancien. */
    @GetMapping
    public List<ConversationSummaryResponse> inbox(@AuthenticationPrincipal AuthPrincipal principal) {
        return conversations.inbox(principal);
    }

    /** Total de non-lus, tous fils confondus (pastille de la navigation). */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("count", conversations.unreadCount(principal));
    }

    /** À qui puis-je écrire ? La liste fait autorité : l'ouverture d'un fil la revérifie. */
    @GetMapping("/recipients")
    public List<RecipientResponse> recipients(@AuthenticationPrincipal AuthPrincipal principal) {
        return conversations.recipients(principal);
    }

    /** Ouvre (ou retrouve) le fil vers un destinataire, un groupe ou le club. */
    @PostMapping("/open")
    public ConversationSummaryResponse open(@AuthenticationPrincipal AuthPrincipal principal,
                                            @Valid @RequestBody com.coachrun.dto.request.OpenConversationRequest request) {
        return conversations.openFor(principal, request.kind(), request.targetId());
    }

    @GetMapping("/{conversationId}/messages")
    public List<MessageResponse> messages(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID conversationId,
                                          @RequestParam(defaultValue = "100") int limit) {
        return conversations.messages(principal, conversationId, limit);
    }

    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse post(@AuthenticationPrincipal AuthPrincipal principal,
                                @PathVariable UUID conversationId,
                                @Valid @RequestBody MessageRequest request) {
        return messageService.postToConversation(principal, conversationId, request);
    }

    @PostMapping("/{conversationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AuthPrincipal principal,
                         @PathVariable UUID conversationId) {
        conversations.markRead(principal, conversationId);
    }

    /** Flux temps réel du fil. Le contrôle d'accès a lieu avant l'abonnement, pas après. */
    @GetMapping("/{conversationId}/stream")
    public SseEmitter stream(@AuthenticationPrincipal AuthPrincipal principal,
                             @PathVariable UUID conversationId) {
        conversations.requireReadable(principal, conversationId);
        return streamService.subscribe(conversationId);
    }
}
