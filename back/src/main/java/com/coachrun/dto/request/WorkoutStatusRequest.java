package com.coachrun.dto.request;

import com.coachrun.entity.enums.WorkoutStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutStatusRequest(@NotNull WorkoutStatus status) {
}
