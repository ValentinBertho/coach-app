package com.coachrun.entity.enums;

/**
 * Préréglage des champs demandés à l'athlète selon son niveau (cf. DARI Lab §7.4).
 * Chaque preset porte le JSON {@code required_fields} correspondant.
 */
public enum FieldsPreset {
    DEBUTANT("{\"charge\":true,\"reps\":true,\"rpe\":false,\"rir\":false,\"pain\":false,\"comment\":true}"),
    AVANCE("{\"charge\":true,\"reps\":true,\"rpe\":true,\"rir\":true,\"pain\":true,\"comment\":true}"),
    REATHLETISATION("{\"charge\":true,\"reps\":true,\"rpe\":true,\"rir\":false,\"pain\":true,\"comment\":true}");

    private final String json;

    FieldsPreset(String json) {
        this.json = json;
    }

    public String json() {
        return json;
    }
}
