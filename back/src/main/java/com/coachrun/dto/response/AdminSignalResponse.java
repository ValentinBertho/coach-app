package com.coachrun.dto.response;

/**
 * Une anomalie actionnable du tableau de bord d'administration.
 *
 * <p><b>Pourquoi des signaux plutôt que des courbes.</b> Un back-office se regarde une minute par
 * jour. Sept compteurs bruts et trois graphiques n'apprennent rien : il faut déjà savoir ce qu'on
 * cherche. Un signal, lui, ne s'affiche que s'il y a quelque chose à faire, dit quoi, et emmène
 * sur l'écran qui le résout.</p>
 *
 * @param key         identifiant stable, pour le suivi côté front
 * @param severity    {@code CRITICAL} (ça casse maintenant) / {@code WARNING} (ça va casser) /
 *                    {@code INFO} (à savoir)
 * @param title       phrase courte, lisible seule
 * @param detail      ce que ça implique concrètement
 * @param actionLabel libellé du lien de résolution, ou {@code null}
 * @param actionRoute route front de résolution, ou {@code null}
 * @param value       grandeur associée (nombre de comptes, pourcentage de plafond…)
 */
public record AdminSignalResponse(
        String key,
        String severity,
        String title,
        String detail,
        String actionLabel,
        String actionRoute,
        long value) {

    public static final String CRITICAL = "CRITICAL";
    public static final String WARNING = "WARNING";
    public static final String INFO = "INFO";

    public static AdminSignalResponse of(String key, String severity, String title, String detail,
                                         String actionLabel, String actionRoute, long value) {
        return new AdminSignalResponse(key, severity, title, detail, actionLabel, actionRoute, value);
    }
}
