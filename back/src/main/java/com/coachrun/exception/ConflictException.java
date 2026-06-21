package com.coachrun.exception;

import org.springframework.http.HttpStatus;

/** Conflit (ex. email déjà utilisé) → 409. */
public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
