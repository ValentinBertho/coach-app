package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrainingPlanRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2048) String description,
        @Min(1) @Max(52) int durationWeeks,
        @Valid List<PlanItemDto> items) {

    public List<PlanItemDto> items() {
        return items == null ? List.of() : items;
    }
}
