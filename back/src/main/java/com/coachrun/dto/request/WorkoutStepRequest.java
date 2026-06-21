package com.coachrun.dto.request;

import com.coachrun.entity.enums.IntensityZone;
import com.coachrun.entity.enums.WorkoutStepType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutStepRequest(
        @NotNull WorkoutStepType stepType,
        @Min(1) int repetitions,
        IntensityZone zone,
        @Min(0) Integer distanceM,
        @Min(0) Integer durationS,
        @Size(max = 512) String notes) {
}
