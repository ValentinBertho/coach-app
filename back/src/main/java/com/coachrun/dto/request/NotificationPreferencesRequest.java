package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Mise à jour partielle des préférences de notification (champs nuls = inchangés). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationPreferencesRequest(Boolean emailEnabled, Boolean pushEnabled) {
}
