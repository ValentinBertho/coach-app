package com.coachrun.dto.response;

import com.coachrun.entity.AthleteZoneValue;
import com.coachrun.entity.enums.ZoneValueSource;

import java.util.UUID;

/** Valeur d'une métrique d'une zone pour un athlète (fourchette min/max, source, verrou). */
public record AthleteZoneValueResponse(
        UUID zoneId,
        /** Nom de la zone dans le modèle de l'athlète : sert à rapprocher une zone d'un autre modèle. */
        String zoneName,
        UUID metricTypeId,
        Double valueMin,
        Double valueMax,
        ZoneValueSource source,
        boolean locked
) {

    public static AthleteZoneValueResponse from(AthleteZoneValue v) {
        return new AthleteZoneValueResponse(
                v.getZone().getId(), v.getZone().getName(), v.getMetricType().getId(),
                v.getValueMin(), v.getValueMax(), v.getSource(), v.isLocked());
    }
}
