package com.coachrun.entity.enums;

/** Nature de la ressource visée par une action d'administration. */
public enum AdminAuditTarget {

    USER("Utilisateur"),
    CLUB("Club"),
    ATHLETE("Athlète"),
    INVITATION("Invitation"),
    PLATFORM("Plateforme");

    private final String label;

    AdminAuditTarget(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
