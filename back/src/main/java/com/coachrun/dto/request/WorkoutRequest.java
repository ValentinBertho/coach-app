package com.coachrun.dto.request;

import com.coachrun.entity.enums.WorkoutType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Création / mise à jour d'une séance prescrite (avec ses étapes structurées). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutRequest(
        @NotNull LocalDate scheduledDate,
        @NotNull WorkoutType type,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2048) String notes,
        @Min(0) Integer targetDistanceM,
        @Min(0) Integer targetDurationS,
        @Valid List<WorkoutStepRequest> steps) {

    public List<WorkoutStepRequest> steps() {
        return steps == null ? List.of() : steps;
    }
}
