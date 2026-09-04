package com.coachrun.controller;

import com.coachrun.dto.request.AthleteRegistrationRequest;
import com.coachrun.dto.response.AuthResponse;
import com.coachrun.service.AthleteAccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * L'inscription d'un athlète qui vient de lui-même.
 *
 * <h2>La différence avec l'invitation</h2>
 *
 * <p>{@code /public/invitations/{token}/accept} <b>active</b> un compte sur une fiche que le coach
 * a déjà créée : la personne existait déjà pour la plateforme. Ici, personne ne l'attend. Aucun
 * club, aucune fiche, aucune relation n'est créé — l'athlète appartient à lui-même jusqu'à ce
 * qu'un coach accepte sa demande.</p>
 *
 * <p>La route est ouverte, et elle déclenche un e-mail : elle relève donc du plafond anonyme
 * d'envoi ({@code RateLimitFilter.isAnonymousEmailTriggering}), au même titre que l'inscription
 * d'un coach et la demande de réinitialisation.</p>
 */
@Tag(name = "Public — Inscription athlète")
@RestController
@RequestMapping("/public/athlete-registration")
@RequiredArgsConstructor
public class PublicAthleteRegistrationController {

    private final AthleteAccountService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody AthleteRegistrationRequest request) {
        return service.register(request);
    }
}
