package com.coachrun.controller;

import com.coachrun.dto.request.MessageRequest;
import com.coachrun.dto.response.MessageResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.MessageService;
import com.coachrun.service.MessageStreamService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Messagerie côté coach (fil d'un athlète). Scoping tenant. */
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}/messages")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class MessageController {

    private final MessageService messageService;
    private final MessageStreamService streamService;

    @GetMapping
    public List<MessageResponse> thread(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return messageService.coachThread(clubId, athleteId);
    }

    /** Flux temps réel (SSE) des nouveaux messages du fil de l'athlète. */
    @GetMapping("/stream")
    public SseEmitter stream(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return streamService.subscribe(athleteId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                @AuthenticationPrincipal AuthPrincipal principal,
                                @Valid @RequestBody MessageRequest request) {
        return messageService.coachSend(clubId, athleteId, principal, request);
    }

    /** Envoi d'un message avec pièce jointe (image/PDF). */
    @PostMapping("/attachment")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendAttachment(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                          @AuthenticationPrincipal AuthPrincipal principal,
                                          @org.springframework.web.bind.annotation.RequestParam(required = false) String body,
                                          @org.springframework.web.bind.annotation.RequestParam("file")
                                          org.springframework.web.multipart.MultipartFile file) {
        return messageService.coachSendWithAttachment(clubId, athleteId, principal, body, file);
    }

    /** Téléchargement d'une pièce jointe. */
    @GetMapping("/{messageId}/attachment")
    public org.springframework.http.ResponseEntity<byte[]> download(
            @PathVariable UUID clubId, @PathVariable UUID athleteId, @PathVariable UUID messageId) {
        return MessageController.toResponse(messageService.attachmentForCoach(clubId, athleteId, messageId));
    }

    static org.springframework.http.ResponseEntity<byte[]> toResponse(com.coachrun.entity.MessageAttachment a) {
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + a.getFilename() + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(a.getContentType()))
                .body(a.getData());
    }
}
