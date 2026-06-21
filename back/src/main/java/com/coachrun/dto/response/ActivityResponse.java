package com.coachrun.dto.response;

import com.coachrun.entity.Activity;
import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.ActivityStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Activité réalisée + rapprochement. Les écarts (distance/durée) sont fournis quand
 * l'activité est rapprochée d'une séance prévue ayant des cibles.
 */
public record ActivityResponse(
        UUID id,
        UUID athleteId,
        ActivitySource source,
        LocalDate activityDate,
        String title,
        Integer distanceM,
        Integer durationS,
        Integer avgHr,
        Integer elevationGainM,
        ActivityStatus status,
        UUID matchedWorkoutId,
        Integer distanceDeltaM,
        Integer durationDeltaS) {

    public static ActivityResponse from(Activity a, Integer distanceDeltaM, Integer durationDeltaS) {
        return new ActivityResponse(
                a.getId(), a.getAthlete().getId(), a.getSource(), a.getActivityDate(), a.getTitle(),
                a.getDistanceM(), a.getDurationS(), a.getAvgHr(), a.getElevationGainM(),
                a.getStatus(), a.getMatchedWorkoutId(), distanceDeltaM, durationDeltaS);
    }

    public static ActivityResponse from(Activity a) {
        return from(a, null, null);
    }
}
