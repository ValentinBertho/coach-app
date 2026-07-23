package com.coachrun.dto.response;

import com.coachrun.entity.MetricType;
import com.coachrun.entity.enums.MetricDirection;
import com.coachrun.entity.enums.MetricFormat;
import com.coachrun.entity.enums.MetricUnit;

import java.util.UUID;

/** Métrique du catalogue (globale ou custom club). {@code clubId} null = métrique globale. */
public record MetricTypeResponse(
        UUID id,
        UUID clubId,
        String code,
        String name,
        MetricUnit unit,
        MetricFormat format,
        MetricDirection direction,
        int sortOrder,
        boolean builtin
) {

    public static MetricTypeResponse from(MetricType m) {
        return new MetricTypeResponse(
                m.getId(),
                m.getClub() == null ? null : m.getClub().getId(),
                m.getCode(), m.getName(), m.getUnit(), m.getFormat(), m.getDirection(),
                m.getSortOrder(), m.isBuiltin());
    }
}
