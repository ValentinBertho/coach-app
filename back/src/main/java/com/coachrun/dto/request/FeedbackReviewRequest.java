package com.coachrun.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Retours sélectionnés dans la boîte de tri, à marquer comme traités d'un seul geste.
 *
 * @param items les retours désignés par leur nature (COURSE ou STRENGTH) et leur séance
 */
public record FeedbackReviewRequest(
        @NotEmpty @Size(max = 500) List<@Valid @NotNull Item> items) {

    public record Item(
            @NotNull @Pattern(regexp = "COURSE|STRENGTH") String kind,
            @NotNull UUID sessionId) {
    }
}
