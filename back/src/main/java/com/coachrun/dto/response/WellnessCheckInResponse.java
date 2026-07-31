package com.coachrun.dto.response;

import com.coachrun.entity.WellnessCheckIn;

import java.time.LocalDate;
import java.util.UUID;

/** Check-in matinal d'un athlète (lecture). */
public record WellnessCheckInResponse(
        UUID id,
        LocalDate checkDate,
        Integer sleep,
        Integer fatigue,
        Integer pain
) {
    public static WellnessCheckInResponse from(WellnessCheckIn c) {
        return new WellnessCheckInResponse(c.getId(), c.getCheckDate(),
                c.getSleep(), c.getFatigue(), c.getPain());
    }
}
