package com.coachrun.controller;

import com.coachrun.service.GarminWebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Adresse de notification Garmin (push). Route {@code /public/**} → non authentifiée : Garmin
 * appelle sans jeton d'utilisateur. L'adresse à déclarer dans l'outil de configuration Garmin
 * ({@code apis.garmin.com/tools/endpoints}) est :
 * {@code https://www.darilab.app/api/public/garmin/webhook?token=<GARMIN_WEBHOOK_TOKEN>}.
 *
 * <p>On répond <b>toujours 200</b>, même jeton faux ou contenu illisible : toute autre réponse
 * fait réessayer Garmin toutes les heures pendant sept jours, sans rien y gagner. Une
 * notification refusée est simplement ignorée, et journalisée.</p>
 */
@Slf4j
@Tag(name = "Sync — Garmin (webhook)")
@RestController
@RequestMapping(GarminWebhookController.PATH)
@RequiredArgsConstructor
public class GarminWebhookController {

    /** Chemin servi, hors préfixe de contexte ({@code /api}). */
    public static final String PATH = "/public/garmin/webhook";

    private final GarminWebhookService webhookService;

    @PostMapping
    public ResponseEntity<Void> notification(@RequestParam(name = "token", required = false) String token,
                                             @RequestBody(required = false) Map<String, Object> body) {
        if (!webhookService.verifies(token)) {
            log.warn("Notification Garmin ignorée : jeton absent ou invalide.");
            return ResponseEntity.ok().build();
        }
        try {
            webhookService.accept(body);
        } catch (RuntimeException ex) {
            log.warn("Notification Garmin illisible, ignorée : {}", ex.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
