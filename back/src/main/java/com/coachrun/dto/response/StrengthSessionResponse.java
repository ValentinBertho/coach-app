package com.coachrun.dto.response;

import com.coachrun.dto.strength.StrengthStructure;
import com.coachrun.entity.StrengthSession;

import java.util.UUID;

/** Séance de force : métadonnées + structure (blocs + exercices prescrits). */
public record StrengthSessionResponse(
        UUID id,
        String name,
        String notes,
        boolean favorite,
        boolean archived,
        int useCount,
        StrengthStructure structure
) {

    public static StrengthSessionResponse of(StrengthSession s, StrengthStructure structure) {
        return new StrengthSessionResponse(
                s.getId(), s.getName(), s.getNotes(), s.isFavorite(), s.isArchived(),
                s.getUseCount(), structure);
    }
}
