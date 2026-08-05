package com.coachrun.dto.response;

import com.coachrun.entity.enums.NotificationCategory;

import java.util.Set;

/**
 * Préférences de notification d'un utilisateur. L'in-app (centre) est toujours actif.
 *
 * <p>{@code usualSessionTime} est au format {@code HH:mm}, ou {@code null} si le rappel de
 * débriefing (« Ta séance est finie ? ») est désactivé. {@code mutedCategories} liste les familles
 * dont le push est coupé, et {@code quietStart} / {@code quietEnd} les heures de silence — trois
 * réglages qui ne portent que sur la notification système, jamais sur le centre.</p>
 */
public record NotificationPreferencesResponse(boolean emailEnabled, boolean pushEnabled,
                                              String usualSessionTime,
                                              Set<NotificationCategory> mutedCategories,
                                              String quietStart, String quietEnd,
                                              String timezone) {
}
