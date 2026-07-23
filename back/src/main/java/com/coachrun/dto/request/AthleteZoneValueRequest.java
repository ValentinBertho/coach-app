package com.coachrun.dto.request;

/**
 * Saisie / ajustement manuel d'une valeur de zone pour un athlète. Fixer une valeur la passe en
 * source MANUAL (protégée du resync). {@code locked} verrouille une valeur (AUTO ou MANUAL).
 */
public record AthleteZoneValueRequest(
        Double valueMin,
        Double valueMax,
        Boolean locked
) {
}
