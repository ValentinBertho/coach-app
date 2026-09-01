package com.coachrun.controller;

import com.coachrun.service.StravaWebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * URL de rappel des événements Strava. Route {@code /public/**} → non authentifiée, par
 * obligation : Strava appelle cette adresse sans jeton et sans en-tête que l'on choisirait.
 *
 * <p>Les deux verbes n'ont rien à voir l'un avec l'autre :</p>
 * <ul>
 *   <li><b>GET</b> — le défi de validation, appelé une seule fois, au moment où l'on crée
 *       l'abonnement. Strava passe un jeton convenu et un défi ; il faut renvoyer le défi tel quel
 *       pour prouver qu'on est bien le propriétaire de l'adresse.</li>
 *   <li><b>POST</b> — les événements, à chaque activité enregistrée. La réponse doit partir en
 *       moins de deux secondes : on acquitte, le travail se fait derrière.</li>
 * </ul>
 */
@Slf4j
@Tag(name = "Sync — Strava (webhook)")
@RestController
@RequestMapping(StravaWebhookPaths.PATH)
@RequiredArgsConstructor
public class StravaWebhookController {

    private final StravaWebhookService webhookService;

    /**
     * Validation de l'adresse par Strava, au moment de créer l'abonnement.
     *
     * <p>Le jeton est vérifié avant de répondre : sans cela, n'importe qui pourrait faire valider
     * cette adresse comme sienne et rediriger le flux d'événements.</p>
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> challenge(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.challenge", required = false) String challenge,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken) {
        if (!"subscribe".equals(mode) || challenge == null || !webhookService.verifies(verifyToken)) {
            log.warn("Défi de validation Strava refusé (mode={}).", mode);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("Défi de validation Strava relevé : l'abonnement peut être créé.");
        // La clé porte bien un point : c'est le nom exact qu'attend Strava.
        return ResponseEntity.ok(Map.of("hub.challenge", challenge));
    }

    /**
     * Événement entrant. On acquitte toujours, même sur un contenu inattendu : un 4xx ou un 5xx
     * compte comme une remise en échec côté Strava, et une série d'échecs coupe l'abonnement pour
     * <b>tous</b> les athlètes. Un événement qu'on ne sait pas lire est un non-événement, pas une
     * panne à signaler.
     */
    @PostMapping
    public ResponseEntity<Void> event(@RequestBody(required = false) Map<String, Object> body) {
        if (body != null) {
            try {
                webhookService.accept(
                        asText(body.get("object_type")),
                        asText(body.get("aspect_type")),
                        asLong(body.get("owner_id")),
                        updatesOf(body.get("updates")));
            } catch (RuntimeException ex) {
                log.warn("Événement Strava illisible, ignoré : {}", ex.getMessage());
            }
        }
        return ResponseEntity.ok().build();
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** {@code owner_id} arrive en nombre, mais on ne parie pas dessus. */
    private static Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> updatesOf(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
