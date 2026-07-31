package com.coachrun.dto.response;

/**
 * Préférences de notification d'un utilisateur. L'in-app (centre) est toujours actif.
 * {@code usualSessionTime} est au format {@code HH:mm}, ou {@code null} si le rappel de
 * débriefing (« Ta séance est finie ? ») est désactivé.
 */
public record NotificationPreferencesResponse(boolean emailEnabled, boolean pushEnabled,
                                              String usualSessionTime) {
}
