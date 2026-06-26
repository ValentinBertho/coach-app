package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrainingPlanRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2048) String description,
        @Min(1) @Max(52) int durationWeeks,
        /** Modèle de mésocycle optionnel : porte une progression de charge appliquée par semaine. */
        UUID mesocycleTemplateId,
        @Valid List<PlanItemDto> items) {

    public List<PlanItemDto> items() {
        return items == null ? List.of() : items;
    }
}
