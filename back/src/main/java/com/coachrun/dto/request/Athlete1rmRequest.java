package com.coachrun.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Renseigne manuellement le 1RM d'un athlète pour un exercice. */
public record Athlete1rmRequest(
        @NotNull UUID exerciseId,
        @NotNull @DecimalMin("0.5") Double rmKg
) {
}
