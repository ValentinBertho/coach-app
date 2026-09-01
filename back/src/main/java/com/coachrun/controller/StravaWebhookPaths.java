package com.coachrun.controller;

/**
 * Le chemin de l'URL de rappel Strava, en un seul endroit.
 *
 * <h2>Pourquoi une constante partagée</h2>
 *
 * <p>Cette adresse est recopiée à la main dans une variable d'environnement, et l'erreur qu'elle
 * provoque est muette : Strava répond « callback url not verifiable » à la création de
 * l'abonnement, sans dire ce qu'il a appelé. L'API est servie derrière un préfixe de contexte
 * ({@code /api}) — la documentation et {@code .env.example} annonçaient pourtant
 * {@code https://api.exemple.app/public/strava/webhook}, sans ce préfixe. L'adresse ainsi posée
 * ne mène à rien, et l'abonnement ne peut pas se créer.</p>
 *
 * <p>La constante ci-dessous est donc lue à la fois par le contrôleur qui sert le webhook, par le
 * contrôleur d'administration qui compose l'adresse attendue et la contrôle avant d'appeler
 * Strava, et par le garde-fou de démarrage qui la compare à la variable posée.</p>
 */
public final class StravaWebhookPaths {

    /** Chemin servi par {@link StravaWebhookController}, <b>hors</b> préfixe de contexte. */
    public static final String PATH = "/public/strava/webhook";

    private StravaWebhookPaths() {
    }

    /**
     * L'adresse complète attendue dans {@code STRAVA_WEBHOOK_CALLBACK_URL}, préfixe de contexte
     * compris.
     *
     * @param publicApiBaseUrl base publique de l'API, sans chemin (ex. {@code https://api.exemple.app})
     * @param contextPath      préfixe de contexte du serveur (ex. {@code /api})
     */
    public static String expectedCallbackUrl(String publicApiBaseUrl, String contextPath) {
        return trimTrailingSlash(publicApiBaseUrl) + fullPath(contextPath);
    }

    /** Chemin complet servi par l'application, préfixe de contexte compris. */
    public static String fullPath(String contextPath) {
        return trimTrailingSlash(contextPath == null ? "" : contextPath.trim()) + PATH;
    }

    private static String trimTrailingSlash(String value) {
        String v = value == null ? "" : value.trim();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
