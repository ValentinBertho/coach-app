package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Création / mise à jour d'un modèle de mésocycle réutilisable. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MesocycleTemplateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2048) String description,
        @NotNull @Min(1) @Max(16) Integer weeks,
        @NotNull @Min(0) @Max(100) Double increasePct,
        @NotNull @Min(2) @Max(16) Integer deloadEvery,
        @NotNull @Min(0) @Max(100) Double deloadPct) {
}
