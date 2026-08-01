package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Test de Vitesse Critique : au moins deux efforts maximaux (distance / temps / FC moyenne). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VcTestRequest(
        @NotEmpty @Size(min = 2, max = 6) @Valid List<Trial> trials,
        boolean applyToProfile
) {
    /**
     * Un effort du test. {@code avgHr} (FC moyenne relevée sur l'effort) est facultative : elle ne
     * change pas le calcul de la VC, mais donne la FC tenue à cette allure — l'autre moitié de
     * l'information dont le coach a besoin pour prescrire.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Trial(
            @Positive double distanceM,
            @Positive double timeS,
            @Min(20) @Max(260) Integer avgHr) {
    }
}
