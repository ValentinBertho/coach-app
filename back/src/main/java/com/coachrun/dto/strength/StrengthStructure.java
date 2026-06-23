package com.coachrun.dto.strength;

import java.util.List;

/**
 * Structure d'une séance de force (cf. DARI Lab) : suite de blocs (échauffement / activation /
 * principal / accessoires / retour au calme). Sérialisée en JSON dans {@code strength_sessions}.
 */
public record StrengthStructure(List<StrengthBlock> blocks) {

    public StrengthStructure {
        blocks = blocks == null ? List.of() : blocks;
    }

    public static StrengthStructure empty() {
        return new StrengthStructure(List.of());
    }
}
