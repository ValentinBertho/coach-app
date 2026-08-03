package com.coachrun.controller;

import com.coachrun.dto.request.InvitationAcceptRequest;
import com.coachrun.dto.response.AuthResponse;
import com.coachrun.dto.response.InvitationInfoResponse;
import com.coachrun.service.AthleteService;
import com.coachrun.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invitation athlète (lien magique). Routes /public/** → non authentifiées :
 * lecture des infos puis acceptation (création du compte ATHLETE + jetons).
 */
@RestController
@RequestMapping("/public/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final AthleteService athleteService;
    private final AuthService authService;

    @GetMapping("/{token}")
    public InvitationInfoResponse info(@PathVariable String token) {
        return athleteService.invitationInfo(token);
    }

    /**
     * Acceptation d'une invitation athlète. Le corps est <b>obligatoire</b> : tant qu'il était
     * facultatif, un POST sans corps créait le compte athlète sans consentement santé (art. 9),
     * puisque le service ne posait l'horodatage que si le booléen valait vrai.
     *
     * <p>Les deux consentements (santé et CGU) sont exigés par la validation du DTO ; le service
     * les horodate, ce qui constitue la preuve exigible.</p>
     */
    @PostMapping("/{token}/accept")
    public AuthResponse accept(@PathVariable String token,
                              @jakarta.validation.Valid @RequestBody
                              InvitationAcceptRequest request) {
        return authService.acceptInvitation(token,
                Boolean.TRUE.equals(request.healthDataConsent()),
                Boolean.TRUE.equals(request.termsAccepted()),
                request.email(),
                request.password());
    }
}
