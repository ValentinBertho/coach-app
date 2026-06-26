package com.coachrun.controller;

import com.coachrun.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Vérification d'e-mail (lien d'inscription). Route /public/** → non authentifiée :
 * confirmation de l'adresse à partir du jeton reçu par e-mail.
 */
@RestController
@RequestMapping("/public/verify-email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final AuthService authService;

    @PostMapping("/{token}")
    public Map<String, Boolean> verify(@PathVariable String token) {
        authService.verifyEmail(token);
        return Map.of("verified", true);
    }
}
