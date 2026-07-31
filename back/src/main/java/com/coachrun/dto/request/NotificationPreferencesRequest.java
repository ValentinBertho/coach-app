package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mise à jour partielle des préférences de notification (champs nuls = inchangés).
 *
 * <p>{@code usualSessionTime} est au format {@code HH:mm} et ancre le rappel « Ta séance est
 * finie ? », envoyé 2 h après. La chaîne vide désactive ce seul rappel sans toucher aux autres
 * canaux — d'où la distinction entre « null » (inchangé) et « vide » (effacé).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationPreferencesRequest(Boolean emailEnabled, Boolean pushEnabled,
                                             String usualSessionTime) {
}
