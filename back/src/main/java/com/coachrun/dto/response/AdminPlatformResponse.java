package com.coachrun.dto.response;

import java.util.List;

/**
 * Configuration de l'instance, en lecture seule.
 *
 * <p><b>Ce qu'elle expose.</b> Uniquement des <i>états</i> : une intégration est configurée ou
 * non, l'envoi d'e-mails est actif ou non, l'inscription est libre ou sur code. Jamais une valeur
 * de secret — ni clé Strava, ni jeton de validation du webhook, ni clé VAPID privée, ni code
 * d'invitation. Savoir qu'un réglage est posé suffit à diagnostiquer ; connaître sa valeur ne sert
 * qu'à la faire fuiter.</p>
 *
 * <p>Elle répond à la question qui suit toujours un incident : « est-ce que c'est configuré ici,
 * sur cette instance ? » — question dont la seule réponse était jusqu'ici d'ouvrir la console
 * d'hébergement.</p>
 */
public record AdminPlatformResponse(
        String environment,
        String version,
        String timezone,
        String frontendUrl,
        String registrationMode,
        long mailDailyQuota,
        long mailMonthlyQuota,
        int mailLogRetentionDays,
        List<Setting> settings) {

    /**
     * Un réglage, réduit à ce qui est diffusable.
     *
     * @param key    identifiant stable
     * @param label  libellé affiché
     * @param state  {@code ON} / {@code OFF} / {@code PARTIAL}
     * @param detail ce que cet état implique concrètement
     * @param source variable d'environnement à modifier, à titre indicatif (jamais sa valeur)
     */
    public record Setting(String key, String label, String state, String detail, String source) {
        public static final String ON = "ON";
        public static final String OFF = "OFF";
        public static final String PARTIAL = "PARTIAL";
    }
}
