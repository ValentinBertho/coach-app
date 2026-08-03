package com.coachrun.entity.enums;

/** Avancement du traitement d'un retour de bêta, côté back-office plateforme. */
public enum FeedbackStatus {
    /** Reçu, pas encore lu. C'est la file du matin. */
    NEW,
    /** Lu et pris en compte (corrigé, planifié, ou répondu). */
    HANDLED
}
