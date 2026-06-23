package com.coachrun.dto.session;

import java.util.List;

/**
 * Structure d'une séance course (cf. DARI Lab) : échauffement / corps de séance / retour au calme.
 * Sérialisée en JSON dans {@code workout_templates.structure_json}.
 */
public record SessionStructure(
        List<CourseBlock> warmup,
        List<CourseBlock> main,
        List<CourseBlock> cooldown
) {

    public SessionStructure {
        warmup = warmup == null ? List.of() : warmup;
        main = main == null ? List.of() : main;
        cooldown = cooldown == null ? List.of() : cooldown;
    }

    public static SessionStructure empty() {
        return new SessionStructure(List.of(), List.of(), List.of());
    }
}
