package com.coachrun.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Création / mise à jour des métadonnées d'une séance de force (nom, notes, favori, catégorie). */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record StrengthSessionRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2048) String notes,
        Boolean favorite,
        /** Catégorie de rangement ; {@code null} laisse — ou remet — la séance sans catégorie. */
        java.util.UUID categoryId
) {
}
