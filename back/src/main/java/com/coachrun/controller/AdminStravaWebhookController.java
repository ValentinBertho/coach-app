package com.coachrun.controller;

import com.coachrun.exception.ApiException;
import com.coachrun.integration.StravaClient;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Gestion de l'abonnement aux événements Strava. Réservé à l'administration de la plateforme.
 *
 * <h2>Pourquoi à la main, et pas au démarrage</h2>
 *
 * <p>Strava n'accepte <b>qu'un seul abonnement par application</b>, et il porte une URL de rappel
 * unique. Si chaque instance créait le sien au démarrage, la production et la préproduction se
 * voleraient le flux à tour de rôle, chaque redéploiement rebasculant les événements de tous les
 * athlètes vers l'instance qui a démarré en dernier. L'abonnement se pose donc une fois, en
 * connaissance de cause, depuis l'environnement qui doit le recevoir.</p>
 */
@Tag(name = "Admin — Webhook Strava")
@RestController
@RequestMapping("/admin/strava/webhook")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminStravaWebhookController {

    private final StravaClient stravaClient;

    /** Adresse publique que Strava appellera : celle de cette instance, terminée par /public/strava/webhook. */
    @Value("${app.strava.webhook-callback-url:}")
    private String callbackUrl;

    @Value("${app.strava.webhook-verify-token:}")
    private String verifyToken;

    /** L'abonnement en place, s'il y en a un, et l'adresse vers laquelle il pointe réellement. */
    @GetMapping
    public Map<String, Object> view() {
        boolean configured = !callbackUrl.isBlank() && !verifyToken.isBlank();
        // Sans identifiants d'application, l'appel partirait à Strava pour se faire refuser : une
        // intégration non configurée est un état normal, pas une erreur à remonter.
        List<StravaClient.WebhookSubscription> subs = stravaClient.isConfigured()
                ? relay(stravaClient::viewWebhookSubscriptions)
                : List.of();
        return Map.of(
                "configured", configured && stravaClient.isConfigured(),
                "callbackUrl", callbackUrl,
                "subscriptions", subs);
    }

    /**
     * Crée l'abonnement. Strava valide l'adresse dans la foulée en appelant le GET du webhook :
     * l'instance doit donc être déployée et joignable depuis l'extérieur avant cet appel.
     */
    @PostMapping
    public StravaClient.WebhookSubscription create() {
        if (callbackUrl.isBlank() || verifyToken.isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Renseigner STRAVA_WEBHOOK_CALLBACK_URL et STRAVA_WEBHOOK_VERIFY_TOKEN, "
                            + "puis redéployer, avant de créer l'abonnement.");
        }
        return relay(() -> stravaClient.createWebhookSubscription(callbackUrl, verifyToken));
    }

    /** Retire l'abonnement : les activités ne remonteront plus que par la synchro planifiée. */
    @DeleteMapping("/{subscriptionId}")
    public Map<String, Boolean> delete(@PathVariable long subscriptionId) {
        relay(() -> {
            stravaClient.deleteWebhookSubscription(subscriptionId);
            return null;
        });
        return Map.of("deleted", true);
    }

    /**
     * Fait remonter le refus de Strava tel quel.
     *
     * <p>Sans cela, le gestionnaire d'erreurs global rangerait l'échec dans « erreur inattendue »
     * et rendrait un identifiant de corrélation : or la seule chose utile ici est la phrase de
     * Strava — « callback url not verifiable » (l'instance n'est pas joignable de l'extérieur),
     * « already exists » (un abonnement occupe déjà la place). C'est exactement ce qu'il faut
     * lire pour savoir quoi corriger.</p>
     *
     * <p>422 et non 502, bien que la panne soit en amont : le client remplace les 5xx par un
     * « service momentanément indisponible » générique, ce qui effacerait justement le message.</p>
     */
    private <T> T relay(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        }
    }
}
