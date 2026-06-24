package com.coachrun.dto.response;

import com.coachrun.entity.StrengthLoadTracking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Point de charge interne d'une séance de force (UA). */
public record StrengthLoadResponse(
        UUID scheduledSessionId,
        LocalDate sessionDate,
        BigDecimal mechanicalLoad,
        BigDecimal metabolicLoad
) {

    public static StrengthLoadResponse from(StrengthLoadTracking t) {
        return new StrengthLoadResponse(
                t.getScheduledSessionId(), t.getSessionDate(),
                t.getMechanicalLoad(), t.getMetabolicLoad());
    }
}
