package com.coachrun.dto.response;

import java.util.UUID;

/**
 * Un plan attribué à l'athlète, vu du portail athlète : identité du plan + avancement
 * ({@code progress} nul si le plan n'a pas d'attribution datée).
 */
public record AthletePlanResponse(
        UUID planId,
        String name,
        String description,
        int durationWeeks,
        PlanProgressResponse progress) {
}
