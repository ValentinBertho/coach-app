package com.coachrun.dto.response;

import com.coachrun.entity.DailyCheckIn;

import java.time.LocalDate;

/** Check-in matinal d'un athlète pour une date donnée. */
public record DailyCheckInResponse(
        LocalDate checkDate,
        Integer sleep,
        Integer fatigue,
        Integer pain
) {

    public static DailyCheckInResponse of(DailyCheckIn c) {
        return new DailyCheckInResponse(c.getCheckDate(), c.getSleep(), c.getFatigue(), c.getPain());
    }
}
