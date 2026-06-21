package com.coachrun.exception;

import org.springframework.http.HttpStatus;

/** Authentification invalide (identifiants/token) → 401. */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
