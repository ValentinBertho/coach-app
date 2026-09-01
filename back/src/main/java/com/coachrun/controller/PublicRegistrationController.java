package com.coachrun.controller;

import com.coachrun.dto.request.ClubCreationRequestSubmission;
import com.coachrun.entity.enums.RegistrationMode;
import com.coachrun.exception.ForbiddenException;
import com.coachrun.service.ClubCreationRequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Ce qu'un visiteur non connecté peut savoir et faire pour entrer sur la plateforme.
 *
 * <h2>Pourquoi le mode d'inscription est public</h2>
 *
 * <p>La page d'inscription doit montrer le bon formulaire : « créer mon club maintenant », « code
 * d'invitation », ou « déposer une demande ». Sans cette route, le front devait deviner — il
 * affichait le formulaire complet, et le candidat découvrait le régime réel au moment du refus,
 * après avoir choisi un mot de passe pour un compte qui n'allait pas exister.</p>
 *
 * <p>Le mode n'est pas un secret : il se déduit en trente secondes en tentant une inscription.
 * Le <b>code</b> d'invitation, lui, n'est jamais rendu.</p>
 */
@Slf4j
@Tag(name = "Public — Inscription")
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicRegistrationController {

    private final ClubCreationRequestService clubCreationRequestService;

    @Value("${app.registration.mode:open}")
    private String registrationMode;

    /** Le régime d'inscription actif, pour que la page d'inscription montre le bon formulaire. */
    @GetMapping("/registration-mode")
    public Map<String, String> registrationMode() {
        RegistrationMode mode = mode();
        return Map.of("mode", mode.name(), "label", mode.label());
    }

    /**
     * Dépôt d'une demande de création de club.
     *
     * <p>202 et non 201 : rien n'est créé. On accuse réception d'une demande à étudier, et c'est
     * exactement ce que le message de l'écran doit dire.</p>
     */
    @PostMapping("/club-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void submit(@Valid @RequestBody ClubCreationRequestSubmission submission,
                       HttpServletRequest httpRequest) {
        if (mode() != RegistrationMode.REQUEST) {
            // Fermé quand la plateforme n'est pas sur ce régime : sans cela, la file se
            // remplirait de demandes que personne ne regarde, pendant que l'inscription
            // directe reste ouverte à côté.
            throw new ForbiddenException(
                    "Cette instance n'accepte pas les demandes de création de club : "
                            + "l'inscription se fait directement depuis la page d'inscription.");
        }
        clubCreationRequestService.submit(submission,
                httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
    }

    /** Le régime actif, avec repli sur le plus fermé si la variable est mal orthographiée. */
    private RegistrationMode mode() {
        RegistrationMode parsed = RegistrationMode.parse(registrationMode);
        if (parsed == null) {
            log.warn("REGISTRATION_MODE=« {} » non reconnu : repli sur « request ».", registrationMode);
            return RegistrationMode.REQUEST;
        }
        return parsed;
    }
}
