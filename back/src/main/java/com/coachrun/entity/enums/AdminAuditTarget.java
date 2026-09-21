package com.coachrun.entity.enums;

/** Nature de la ressource visée par une action d'administration. */
public enum AdminAuditTarget {

    USER("Utilisateur"),
    CLUB("Club"),
    ATHLETE("Athlète"),
    INVITATION("Invitation"),
    /** Ajoutée avec les gestes de coaching : un plan supprimé n'est ni un compte ni un club. */
    TRAINING_PLAN("Plan d'entraînement"),
    PLATFORM("Plateforme");

    private final String label;

    AdminAuditTarget(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
