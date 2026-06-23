package com.coachrun.dto.response;

/**
 * Charge cible calculée d'un exercice de force : fourchette en kg + libellés.
 * {@code computable=false} si la charge dépend d'un %RM mais que le 1RM de l'athlète est absent.
 */
public record ChargeTarget(
        boolean computable,
        Double oneRmKg,
        Double kgMin,
        Double kgMax,
        String chargeLabel,
        String effortLabel
) {
}
