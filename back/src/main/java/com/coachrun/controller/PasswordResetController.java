package com.coachrun.controller;

import com.coachrun.dto.request.ForgotPasswordRequest;
import com.coachrun.dto.request.PasswordResetRequest;
import com.coachrun.dto.response.AuthResponse;
import com.coachrun.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Réinitialisation de mot de passe (lien magique). Routes /public/** non authentifiées.
 * La demande renvoie toujours 200 (ne révèle pas l'existence d'un compte).
 */
@RestController
@RequestMapping("/public/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final AuthService authService;

    @PostMapping
    public Map<String, Boolean> request(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.email());
        return Map.of("ok", true);
    }

    @GetMapping("/{token}")
    public Map<String, Boolean> validate(@PathVariable String token) {
        return Map.of("valid", authService.resetTokenValid(token));
    }

    @PostMapping("/{token}")
    public AuthResponse reset(@PathVariable String token, @Valid @RequestBody PasswordResetRequest request) {
        return authService.resetPassword(token, request.password());
    }
}
