package com.coachrun.controller;

import com.coachrun.dto.response.InvitationInfoResponse;
import com.coachrun.service.AthleteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lecture publique d'une invitation (page d'acceptation athlète via lien magique).
 * Route /public/** → non authentifiée.
 */
@RestController
@RequestMapping("/public/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final AthleteService athleteService;

    @GetMapping("/{token}")
    public InvitationInfoResponse info(@PathVariable String token) {
        return athleteService.invitationInfo(token);
    }
}
