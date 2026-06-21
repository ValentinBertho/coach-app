package com.coachrun.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Corps d'erreur JSON normalisé renvoyé par {@link GlobalExceptionHandler}.
 * {@code fieldErrors} et {@code correlationId} sont optionnels selon le type d'erreur.
 */
public record ApiError(
        int status,
        String message,
        String path,
        Instant timestamp,
        Map<String, String> fieldErrors,
        String correlationId) {

    public static ApiError of(int status, String message, String path) {
        return new ApiError(status, message, path, Instant.now(), null, null);
    }
}
