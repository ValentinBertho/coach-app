package com.coachrun.controller;

import com.coachrun.dto.request.InviteJoinRequest;
import com.coachrun.dto.request.InviteLinkRequest;
import com.coachrun.dto.response.InviteJoinResponse;
import com.coachrun.dto.response.InviteLinkResponse;
import com.coachrun.dto.response.InvitePreviewResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.InviteLinkService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Liens d'invitation : création côté coach, page publique, entrée côté athlète. */
@RestController
@RequiredArgsConstructor
public class InviteLinkController {

    private final InviteLinkService service;

    @GetMapping("/me/invite-links")
    @PreAuthorize("hasAnyRole('COACH','HEAD_COACH')")
    public List<InviteLinkResponse> mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.mine(principal.userId());
    }

    @PostMapping("/me/invite-links")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('COACH','HEAD_COACH')")
    public InviteLinkResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody InviteLinkRequest request) {
        return service.create(principal.userId(), principal.clubId(), request);
    }

    @DeleteMapping("/me/invite-links/{id}")
    @PreAuthorize("hasAnyRole('COACH','HEAD_COACH')")
    public InviteLinkResponse revoke(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return service.revoke(principal.userId(), id);
    }

    @GetMapping("/public/invites/{token}")
    public InvitePreviewResponse preview(@PathVariable String token) {
        return service.preview(token);
    }

    @PostMapping("/me/invites/{token}/join")
    @PreAuthorize("hasRole('ATHLETE')")
    public InviteJoinResponse join(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String token,
                                   @Valid @RequestBody(required = false) InviteJoinRequest request) {
        return service.join(principal.userId(), token, request != null ? request.message() : null);
    }
}
