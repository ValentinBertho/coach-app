package com.coachrun.exception;

import org.springframework.http.HttpStatus;

/** Ressource introuvable (ou hors tenant) → 404. */
public class NotFoundException extends ApiException {
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
