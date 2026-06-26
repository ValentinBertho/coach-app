package com.coachrun.controller;

import com.coachrun.dto.request.CoachInvitationAcceptRequest;
import com.coachrun.dto.response.AuthResponse;
import com.coachrun.dto.response.CoachInvitationInfoResponse;
import com.coachrun.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invitation coach (lien magique). Routes /public/** → non authentifiées : lecture des infos
 * puis acceptation (définition du mot de passe + activation du compte coach + jetons).
 */
@RestController
@RequestMapping("/public/coach-invitations")
@RequiredArgsConstructor
public class CoachInvitationController {

    private final AuthService authService;

    @GetMapping("/{token}")
    public CoachInvitationInfoResponse info(@PathVariable String token) {
        return authService.coachInvitationInfo(token);
    }

    @PostMapping("/{token}/accept")
    public AuthResponse accept(@PathVariable String token,
                               @Valid @RequestBody CoachInvitationAcceptRequest request) {
        return authService.acceptCoachInvitation(token, request.password(), request.fullName());
    }
}
