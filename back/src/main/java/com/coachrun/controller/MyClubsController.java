package com.coachrun.controller;

import com.coachrun.dto.response.MyClubResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.MyClubsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Les espaces de travail du coach connecté.
 *
 * <p>La pièce qui manquait au modèle multi-club. Tout le reste existait — l'adhésion, le rôle, le
 * validateur d'accès qui accepte les clubs additionnels, l'API scopée par club — sauf le moyen,
 * pour l'interface, de <b>savoir</b> qu'un second espace existe.</p>
 */
@Tag(name = "Coach — Mes espaces")
@RestController
@RequestMapping("/me/clubs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COACH','HEAD_COACH')")
public class MyClubsController {

    private final MyClubsService service;

    @GetMapping
    public List<MyClubResponse> myClubs(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.myClubs(principal.userId());
    }
}
