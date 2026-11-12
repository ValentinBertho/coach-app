package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Un diplôme déclaré par le coach. La plateforme ne s'en porte pas garante. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoachCertificationRequest(
        @NotBlank @Size(max = 200) String label,
        @Size(max = 200) String organisation,
        @Min(1950) @Max(2100) Integer obtainedYear) {
}
