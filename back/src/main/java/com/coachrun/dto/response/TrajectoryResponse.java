package com.coachrun.dto.response;

import com.coachrun.engine.TrajectoryEngine.Outlook;
import com.coachrun.engine.TrajectoryEngine.Trajectory;

import java.time.LocalDate;
import java.util.UUID;

/**
 * La trajectoire vers la prochaine course : l'écart au chrono visé, et sa cause.
 *
 * <p>{@code loadNote} n'apparaît que lorsqu'il y a quelque chose à dire de la charge — une
 * stagnation, une chute. Une ligne « ta charge est normale » n'aide personne et occupe la place de
 * celle qui compte.</p>
 */
public record TrajectoryResponse(
        UUID raceId,
        String raceName,
        LocalDate raceDate,
        int daysLeft,
        int weeksLeft,
        Integer distanceM,
        Integer targetTimeS,
        Outlook outlook,
        Double currentVdot,
        Double requiredVdot,
        Integer projectedTimeS,
        Integer gapSeconds,
        Double vdotGap,
        Double vdotPerWeek,
        String headline,
        String detail,
        String loadNote
) {

    public static TrajectoryResponse none() {
        return null;
    }

    public static TrajectoryResponse from(UUID raceId, String raceName, LocalDate raceDate,
                                          int daysLeft, Integer distanceM, Integer targetTimeS,
                                          Trajectory t, String loadNote) {
        return new TrajectoryResponse(
                raceId, raceName, raceDate, daysLeft, t.weeksLeft(), distanceM, targetTimeS,
                t.outlook(), t.currentVdot(), t.requiredVdot(), t.projectedTimeS(),
                t.gapSeconds(), t.vdotGap(), t.vdotPerWeek(), t.headline(), t.detail(), loadNote);
    }
}
