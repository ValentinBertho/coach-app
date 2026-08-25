package com.coachrun.dto.response;

/**
 * État d'une intégration ou d'un canal sortant, tel qu'on peut le connaître <b>sans appeler le
 * tiers</b>.
 *
 * <p>Le tableau de bord ne doit pas dépendre de la disponibilité de Strava ou du fournisseur
 * d'e-mail pour s'afficher : un appel sortant lent y rendrait la page inutilisable au moment
 * précis où on en a besoin. On rapporte donc la configuration et la consommation mesurée en base ;
 * la vérification en direct reste un geste explicite (écran Strava, journal d'e-mails).</p>
 *
 * @param key    identifiant stable ({@code strava}, {@code mail}, {@code push})
 * @param label  nom affiché
 * @param status {@code OK} / {@code WARNING} / {@code OFF}
 * @param detail phrase qui dit ce que l'état implique
 * @param count  grandeur associée (connexions, abonnements, envois du jour)
 */
public record AdminIntegrationResponse(
        String key,
        String label,
        String status,
        String detail,
        long count) {

    public static final String OK = "OK";
    public static final String WARNING = "WARNING";
    public static final String OFF = "OFF";
}
