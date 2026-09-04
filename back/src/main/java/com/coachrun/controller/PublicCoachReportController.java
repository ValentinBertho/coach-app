package com.coachrun.controller;

import com.coachrun.dto.request.CoachReportSubmission;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.CoachReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signaler une fiche coach.
 *
 * <h2>Pourquoi la route est ouverte</h2>
 *
 * <p>Exiger un compte écarterait précisément ceux qui ont le plus de raisons de signaler : le
 * confrère qui reconnaît un diplôme qu'il sait faux, l'ancien athlète qui ne veut plus rien avoir à
 * faire avec la plateforme. Le principal est néanmoins lu s'il existe — le filtre JWT s'exécute
 * aussi sur les routes publiques — et un signalement nominatif ne se pèse pas comme un anonyme.</p>
 *
 * <h2>Ce que la réponse ne dit pas</h2>
 *
 * <p>204, et rien d'autre. Renvoyer l'objet créé donnerait à qui signale un identifiant, donc un
 * moyen de vérifier plus tard ce qu'il est devenu ; l'arbitrage d'un signalement contient des
 * éléments sur le coach qui n'appartiennent pas au signalant.</p>
 */
@Tag(name = "Public — Signalement d'une fiche coach")
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicCoachReportController {

    private final CoachReportService service;

    @PostMapping("/coaches/{slug}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@PathVariable String slug,
                       @Valid @RequestBody CoachReportSubmission submission,
                       @AuthenticationPrincipal AuthPrincipal principal,
                       HttpServletRequest http) {
        service.submit(slug, submission,
                principal == null ? null : principal.userId(),
                http.getRemoteAddr(), http.getHeader("User-Agent"));
    }
}
