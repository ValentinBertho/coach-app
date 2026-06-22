package com.coachrun.dto.request;

import com.coachrun.entity.enums.RaceObjectiveStatus;
import com.coachrun.entity.enums.RacePriority;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RaceObjectiveRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull LocalDate raceDate,
        @Min(0) Integer distanceM,
        @Min(0) Integer targetTimeS,
        RacePriority priority,
        RaceObjectiveStatus status) {
}
