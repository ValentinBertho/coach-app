package com.coachrun.exception;

import org.springframework.http.HttpStatus;

/**
 * Action refusée alors que la requête est bien formée et l'appelant identifiable — par exemple
 * une inscription sans code valide pendant la bêta fermée. Distinct de l'échec d'authentification
 * (401) : le message porte la raison, que le client affiche tel quel.
 */
public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
