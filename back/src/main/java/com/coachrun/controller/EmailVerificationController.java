package com.coachrun.controller;

import com.coachrun.service.AuthService;
import com.coachrun.service.CoachingRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Vérification d'e-mail (lien d'inscription). Route /public/** → non authentifiée :
 * confirmation de l'adresse à partir du jeton reçu par e-mail.
 */
@RestController
@RequestMapping("/public/verify-email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final AuthService authService;
    private final CoachingRequestService coachingRequests;

    /**
     * Confirme l'adresse, puis envoie la demande de coaching que l'athlète avait préparée en
     * s'inscrivant, s'il y en a une.
     *
     * <p>Deux transactions, dans cet ordre : la vérification est acquise avant que l'envoi ne soit
     * tenté. {@code requestSentTo} porte le nom du coach quand une demande vient de partir, pour
     * que l'écran puisse le dire ; il est absent sinon.</p>
     */
    @PostMapping("/{token}")
    public Map<String, Object> verify(@PathVariable String token) {
        UUID userId = authService.verifyEmail(token);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("verified", true);
        coachingRequests.sendPendingAfterVerification(userId)
                .ifPresent(coach -> body.put("requestSentTo", coach));
        return body;
    }
}
