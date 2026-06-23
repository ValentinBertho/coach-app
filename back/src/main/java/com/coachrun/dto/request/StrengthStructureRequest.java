package com.coachrun.dto.request;

import com.coachrun.dto.strength.StrengthStructure;

/** Mise à jour de la structure DARI Lab d'une séance de force (blocs + exercices prescrits). */
public record StrengthStructureRequest(StrengthStructure structure) {
}
