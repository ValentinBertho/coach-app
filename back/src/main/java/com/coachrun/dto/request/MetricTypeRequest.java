package com.coachrun.dto.request;

import com.coachrun.entity.enums.MetricDirection;
import com.coachrun.entity.enums.MetricFormat;
import com.coachrun.entity.enums.MetricUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Création d'une métrique custom rattachée au club (le catalogue global reste en lecture seule). */
public record MetricTypeRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 255) String name,
        @NotNull MetricUnit unit,
        @NotNull MetricFormat format,
        @NotNull MetricDirection direction,
        Integer sortOrder
) {
}
