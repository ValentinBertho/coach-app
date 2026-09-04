package com.coachrun.dto.request;

import com.coachrun.entity.enums.OfferPeriodicity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Une formule de coaching.
 *
 * <p>Le montant est en centimes. Le plafond à 100 000 € n'est pas une opinion sur les tarifs :
 * c'est un garde-fou contre la saisie d'un montant déjà en centimes, qui afficherait « 9 000 € »
 * pour « 90 € » sur une fiche publique.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoachOfferRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @Min(0) @Max(10_000_000) int amountCents,
        @NotNull OfferPeriodicity periodicity,
        boolean active,
        @Min(0) @Max(100) int position) {
}
