package com.coachrun.dto.request;

import com.coachrun.entity.enums.ZoneAnchor;
import com.coachrun.entity.enums.ZoneModel;

/**
 * Règle de calcul d'une métrique de zone : ancre + fourchette % + modèle nommé (chantier zones v2).
 *
 * <p>{@code highAnchor} n'est utile que pour la zone qui enjambe la frontière LT1 → LT2 : sa borne
 * basse s'exprime en % de LT1 et sa borne haute en % de LT2. Laissé vide, la borne haute reprend
 * {@code anchor}.</p>
 */
public record ZoneRuleRequest(
        ZoneAnchor anchor,
        ZoneAnchor highAnchor,
        Double lowPct,
        Double highPct,
        ZoneModel model
) {
}
