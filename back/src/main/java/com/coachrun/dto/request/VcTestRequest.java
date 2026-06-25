package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Test de Vitesse Critique : au moins deux efforts maximaux (distance / temps). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VcTestRequest(
        @NotEmpty @Size(min = 2, max = 6) @Valid List<Trial> trials,
        boolean applyToProfile
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Trial(@Positive double distanceM, @Positive double timeS) {}
}
