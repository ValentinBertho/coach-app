package com.coachrun.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Création / mise à jour des métadonnées d'une séance de force (nom, notes, favori). */
public record StrengthSessionRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2048) String notes,
        Boolean favorite
) {
}
