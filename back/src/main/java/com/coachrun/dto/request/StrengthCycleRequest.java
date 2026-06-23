package com.coachrun.dto.request;

import com.coachrun.dto.strength.CycleStructure;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Création / mise à jour d'un cycle de force. */
public record StrengthCycleRequest(
        @NotBlank @Size(max = 255) String name,
        @Min(1) int weeks,
        String objective,
        @Size(max = 2048) String description,
        CycleStructure structure
) {
}
