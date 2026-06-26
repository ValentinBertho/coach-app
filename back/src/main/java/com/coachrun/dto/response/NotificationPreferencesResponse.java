package com.coachrun.dto.response;

/** Préférences de notification d'un utilisateur. L'in-app (centre) est toujours actif. */
public record NotificationPreferencesResponse(boolean emailEnabled, boolean pushEnabled) {
}
