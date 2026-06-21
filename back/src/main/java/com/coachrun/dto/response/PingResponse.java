package com.coachrun.dto.response;

/**
 * Réponse de l'endpoint de démo {@code GET /api/public/ping} : preuve de vie de l'API.
 */
public record PingResponse(String status, String version) {
}
