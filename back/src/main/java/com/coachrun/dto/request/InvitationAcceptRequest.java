package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Acceptation d'invitation : consentement explicite à la collecte des données de santé
 * (RGPD art. 9). {@code healthDataConsent} doit être true pour activer le suivi physiologique.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InvitationAcceptRequest(boolean healthDataConsent) {
}
