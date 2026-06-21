package com.coachrun.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception métier portant un statut HTTP. Sous-classes pour les cas courants.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
