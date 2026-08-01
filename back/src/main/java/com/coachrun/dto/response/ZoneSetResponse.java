package com.coachrun.dto.response;

import com.coachrun.entity.ZoneSet;

import java.util.UUID;

/** Modèle de zones d'un club, avec le nombre de zones qu'il porte. */
public record ZoneSetResponse(
        UUID id,
        String name,
        int sortOrder,
        /** Jeu appliqué aux athlètes qui n'en désignent aucun — non supprimable. */
        boolean isDefault,
        int zoneCount
) {

    public static ZoneSetResponse of(ZoneSet s, int zoneCount) {
        return new ZoneSetResponse(s.getId(), s.getName(), s.getSortOrder(), s.isDefaultSet(), zoneCount);
    }
}
