package com.coachrun.dto.request;

import com.coachrun.entity.enums.ClubStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClubRequest(
        @NotBlank @Size(max = 120) String name,
        ClubStatus status) {
}
