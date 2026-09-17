package com.coachrun.dto.response;

import com.coachrun.dto.strength.StrengthStructure;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Prescription figée d'une séance de force planifiée : snapshot des blocs, charges calculées et
 * champs demandés à l'athlète.
 *
 * <p>Le {@code title} voyage avec la prescription — comme du côté course — parce que l'écran qui
 * modifie le contenu d'une séance planifiée doit aussi pouvoir en afficher et en changer le nom
 * sans redemander le résumé du calendrier.</p>
 */
public record StrengthPrescriptionResponse(
        String title,
        StrengthStructure snapshot,
        CalculatedStrengthResponse calculated,
        JsonNode requiredFields
) {
}
