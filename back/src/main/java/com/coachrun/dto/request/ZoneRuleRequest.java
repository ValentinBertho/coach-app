package com.coachrun.dto.request;

import com.coachrun.entity.enums.ZoneAnchor;
import com.coachrun.entity.enums.ZoneModel;

/** Règle de calcul d'une métrique de zone : ancre + fourchette % + modèle nommé (chantier zones v2). */
public record ZoneRuleRequest(
        ZoneAnchor anchor,
        Double lowPct,
        Double highPct,
        ZoneModel model
) {
}
