package com.coachrun.dto.response;

import com.coachrun.entity.RaceObjective;
import com.coachrun.entity.enums.RaceObjectiveStatus;
import com.coachrun.entity.enums.RacePriority;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record RaceObjectiveResponse(
        UUID id,
        UUID athleteId,
        String name,
        LocalDate raceDate,
        Integer distanceM,
        Integer targetTimeS,
        RacePriority priority,
        RaceObjectiveStatus status,
        long daysUntil) {

    public static RaceObjectiveResponse from(RaceObjective r) {
        return new RaceObjectiveResponse(
                r.getId(), r.getAthlete().getId(), r.getName(), r.getRaceDate(),
                r.getDistanceM(), r.getTargetTimeS(), r.getPriority(), r.getStatus(),
                ChronoUnit.DAYS.between(LocalDate.now(), r.getRaceDate()));
    }
}
