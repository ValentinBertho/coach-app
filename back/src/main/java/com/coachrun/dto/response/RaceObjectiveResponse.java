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
        /** Nom de l'athlète : renseigné sur les listes multi-athlètes (cockpit coach). */
        String athleteName,
        String name,
        LocalDate raceDate,
        Integer distanceM,
        Integer targetTimeS,
        RacePriority priority,
        RaceObjectiveStatus status,
        long daysUntil) {

    /**
     * @param today date du jour dans le fuseau métier ({@code ClockService.today()}) — le compte à
     *              rebours est faux d'un jour si on le calcule en UTC après 22 h.
     */
    public static RaceObjectiveResponse from(RaceObjective r, LocalDate today) {
        return from(r, null, today);
    }

    public static RaceObjectiveResponse from(RaceObjective r, String athleteName, LocalDate today) {
        return new RaceObjectiveResponse(
                r.getId(), r.getAthlete().getId(), athleteName, r.getName(), r.getRaceDate(),
                r.getDistanceM(), r.getTargetTimeS(), r.getPriority(), r.getStatus(),
                ChronoUnit.DAYS.between(today, r.getRaceDate()));
    }
}
