package com.coachrun.controller;

import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.exception.ApiException;
import com.coachrun.integration.StravaClient;
import com.coachrun.service.AdminAuditService;
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
    private final AdminAuditService adminAuditService;

    /** Adresse publique que Strava appellera : celle de cette instance, terminée par /public/strava/webhook. */
    @Value("${app.strava.webhook-callback-url:}")
    private String callbackUrl;

    @Value("${app.strava.webhook-verify-token:}")
    private String verifyToken;

    /**
     * Préfixe de contexte du serveur ({@code /api}). Il fait partie de l'adresse publique du
     * webhook, et c'est précisément ce qu'on oublie en recopiant la variable.
     */
    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /** L'abonnement en place, s'il y en a un, et l'adresse vers laquelle il pointe réellement. */
    @GetMapping
    public Map<String, Object> view() {
        boolean configured = !callbackUrl.isBlank() && !verifyToken.isBlank();
        // Sans identifiants d'application, l'appel partirait à Strava pour se faire refuser : une
        // intégration non configurée est un état normal, pas une erreur à remonter.
        List<StravaClient.WebhookSubscription> subs = stravaClient.isConfigured()
                ? relay(stravaClient::viewWebhookSubscriptions)
                : List.of();
        // « callbackUrlProblem » est renseigné quand l'adresse posée ne peut pas fonctionner :
        // l'écran l'affiche AVANT le bouton « Activer », plutôt que de laisser l'exploitant
        // découvrir « callback url not verifiable » sans savoir ce que Strava a appelé.
        String problem = callbackUrlProblem();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("configured", configured && stravaClient.isConfigured());
        body.put("callbackUrl", callbackUrl);
        body.put("expectedPath", StravaWebhookPaths.fullPath(contextPath));
        body.put("callbackUrlProblem", problem);
        body.put("subscriptions", subs);
        return body;
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
        // Contrôlé ICI, avant de partir chez Strava : son refus est « callback url not
        // verifiable », qui ne dit ni quelle adresse a été appelée, ni pourquoi elle a échoué.
        String problem = callbackUrlProblem();
        if (problem != null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, problem);
        }
        StravaClient.WebhookSubscription created =
                relay(() -> stravaClient.createWebhookSubscription(callbackUrl, verifyToken));
        // L'abonnement est unique par application Strava : le poser depuis une instance le retire
        // de fait à toutes les autres. Le journal dit laquelle, et quand.
        adminAuditService.recordPlatform(AdminAuditAction.STRAVA_WEBHOOK_CREATED,
                "Abonnement " + created.id() + " vers " + callbackUrl);
        return created;
    }

    /** Retire l'abonnement : les activités ne remonteront plus que par la synchro planifiée. */
    @DeleteMapping("/{subscriptionId}")
    public Map<String, Boolean> delete(@PathVariable long subscriptionId) {
        relay(() -> {
            stravaClient.deleteWebhookSubscription(subscriptionId);
            return null;
        });
        adminAuditService.recordPlatform(AdminAuditAction.STRAVA_WEBHOOK_DELETED,
                "Abonnement " + subscriptionId
                        + " retiré — les activités ne remontent plus que par la passe horaire.");
        return Map.of("deleted", true);
    }

    /**
     * Ce qui empêche l'adresse posée de fonctionner, ou {@code null} si elle est plausible.
     *
     * <h2>L'erreur que ce contrôle attrape</h2>
     *
     * <p>L'API est servie derrière le préfixe {@code /api} ({@code server.servlet.context-path}).
     * L'adresse du webhook est donc {@code https://api.exemple.app/api/public/strava/webhook} —
     * or la documentation et {@code .env.example} donnaient la variante <b>sans</b> ce préfixe.
     * Posée telle quelle, l'adresse renvoie une 404 : Strava la valide par un GET immédiat, ne
     * la trouve pas, et refuse l'abonnement avec « callback url not verifiable ». Rien, dans ce
     * message, ne désigne le préfixe manquant.</p>
     *
     * <p>Strava exige par ailleurs une adresse publique en clair : ni {@code localhost}, ni une
     * adresse privée, qu'il ne pourrait pas joindre depuis l'extérieur.</p>
     */
    private String callbackUrlProblem() {
        if (callbackUrl.isBlank()) {
            return null; // absence traitée à part, avec son propre message
        }
        String expectedPath = StravaWebhookPaths.fullPath(contextPath);
        java.net.URI uri;
        try {
            uri = java.net.URI.create(callbackUrl.trim());
        } catch (IllegalArgumentException ex) {
            return "STRAVA_WEBHOOK_CALLBACK_URL n'est pas une URL valide : « " + callbackUrl + " ».";
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || scheme == null || !scheme.equalsIgnoreCase("https")) {
            return "STRAVA_WEBHOOK_CALLBACK_URL doit être une adresse https publique complète "
                    + "(« https://api.mon-domaine.app" + expectedPath + " »), et non « "
                    + callbackUrl + " ».";
        }
        if (host.equalsIgnoreCase("localhost") || host.startsWith("127.")
                || host.startsWith("192.168.") || host.startsWith("10.")) {
            return "STRAVA_WEBHOOK_CALLBACK_URL pointe sur une adresse privée (« " + host
                    + " ») : Strava valide l'adresse depuis l'extérieur et ne peut pas l'atteindre.";
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!expectedPath.equals(path)) {
            return "STRAVA_WEBHOOK_CALLBACK_URL se termine par « " + (path.isEmpty() ? "/" : path)
                    + " » au lieu de « " + expectedPath + " ». L'API est servie derrière le "
                    + "préfixe « " + contextPath + " » : sans lui, Strava valide l'adresse sur "
                    + "une page inexistante et refuse l'abonnement (« callback url not "
                    + "verifiable »). Adresse attendue : "
                    + StravaWebhookPaths.expectedCallbackUrl(
                            scheme + "://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : ""),
                            contextPath)
                    + " — puis redéployer.";
        }
        return null;
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
