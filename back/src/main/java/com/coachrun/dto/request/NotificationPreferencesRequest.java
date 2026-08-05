package com.coachrun.dto.request;

import com.coachrun.entity.enums.NotificationCategory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;

/**
 * Mise à jour partielle des préférences de notification (champs nuls = inchangés).
 *
 * <p>{@code usualSessionTime} est au format {@code HH:mm} et ancre le rappel « Ta séance est
 * finie ? », envoyé 2 h après. La chaîne vide désactive ce seul rappel sans toucher aux autres
 * canaux — d'où la distinction entre « null » (inchangé) et « vide » (effacé).</p>
 *
 * <p>{@code mutedCategories} est la liste <b>complète</b> des familles dont le push est coupé :
 * elle remplace la précédente, elle ne s'y ajoute pas, et une liste vide réactive tout. Le centre
 * de notifications reste alimenté quoi qu'il arrive.</p>
 *
 * <p>{@code quietStart} / {@code quietEnd} bornent les heures de silence, au format {@code HH:mm}.
 * Deux bornes identiques valent « pas de silence » ; la chaîne vide efface la borne.</p>
 *
 * <p>{@code timezone} est un identifiant IANA ({@code Europe/Paris}) ; la chaîne vide revient au
 * fuseau de l'application.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationPreferencesRequest(Boolean emailEnabled, Boolean pushEnabled,
                                             String usualSessionTime,
                                             Set<NotificationCategory> mutedCategories,
                                             String quietStart, String quietEnd,
                                             String timezone) {
}
