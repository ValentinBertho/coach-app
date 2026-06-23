package com.coachrun.dto.response;

import com.coachrun.dto.strength.CycleStructure;
import com.coachrun.entity.StrengthCycle;

import java.util.UUID;

/** Cycle de force avec sa structure (semaines). */
public record StrengthCycleResponse(
        UUID id,
        String name,
        int weeks,
        String objective,
        String description,
        CycleStructure structure
) {
    public static StrengthCycleResponse of(StrengthCycle c, CycleStructure structure) {
        return new StrengthCycleResponse(c.getId(), c.getName(), c.getWeeks(),
                c.getObjective(), c.getDescription(), structure);
    }
}
