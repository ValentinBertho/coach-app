package com.coachrun.dto.response;

import com.coachrun.entity.CoachOffer;
import com.coachrun.entity.enums.OfferPeriodicity;

import java.util.UUID;

/**
 * Une formule, prête à afficher.
 *
 * <p>{@code suffix} et {@code monthlyEquivalentCents} sont calculés ici plutôt que dans chaque
 * écran : le premier pour que « 90 € / mois » s'écrive partout pareil, le second parce que
 * comparer des formules qui ne se paient pas au même rythme est le travail du serveur, pas celui
 * du navigateur. {@code null} quand la formule n'a pas d'équivalent mensuel honnête (à la séance,
 * ou forfait unique).</p>
 */
public record CoachOfferResponse(
        UUID id,
        String name,
        String description,
        int amountCents,
        String currency,
        OfferPeriodicity periodicity,
        String suffix,
        Integer monthlyEquivalentCents,
        boolean active,
        int position) {

    public static CoachOfferResponse from(CoachOffer o) {
        return new CoachOfferResponse(
                o.getId(),
                o.getName(),
                o.getDescription(),
                o.getAmountCents(),
                o.getCurrency(),
                o.getPeriodicity(),
                o.getPeriodicity().suffix(),
                o.getPeriodicity().monthlyEquivalentCents(o.getAmountCents()),
                o.isActive(),
                o.getPosition());
    }
}
