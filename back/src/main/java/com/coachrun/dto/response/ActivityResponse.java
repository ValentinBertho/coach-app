package com.coachrun.dto.response;

import com.coachrun.entity.Activity;
import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.ActivityStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Activité réalisée + rapprochement. Les écarts (distance/durée) sont fournis quand
 * l'activité est rapprochée d'une séance prévue ayant des cibles.
 *
 * @param paceSPerKm allure moyenne en secondes par kilomètre, dérivée de la durée et de la
 *                   distance. Le coureur raisonne en allure, pas en « 47 min pour 10,2 km » :
 *                   le calcul est fait ici pour que tous les écrans affichent la même valeur.
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
        Integer maxHr,
        Integer avgCadence,
        Integer avgPowerW,
        Integer calories,
        Integer paceSPerKm,
        ActivityStatus status,
        UUID matchedWorkoutId,
        Integer distanceDeltaM,
        Integer durationDeltaS) {

    public static ActivityResponse from(Activity a, Integer distanceDeltaM, Integer durationDeltaS) {
        return new ActivityResponse(
                a.getId(), a.getAthlete().getId(), a.getSource(), a.getActivityDate(), a.getTitle(),
                a.getDistanceM(), a.getDurationS(), a.getAvgHr(), a.getElevationGainM(),
                a.getMaxHr(), a.getAvgCadence(), a.getAvgPowerW(), a.getCalories(),
                pace(a.getDistanceM(), a.getDurationS()),
                a.getStatus(), a.getMatchedWorkoutId(), distanceDeltaM, durationDeltaS);
    }

    /** Allure moyenne (s/km), ou {@code null} si distance ou durée manquent. */
    private static Integer pace(Integer distanceM, Integer durationS) {
        if (distanceM == null || durationS == null || distanceM <= 0 || durationS <= 0) {
            return null;
        }
        return (int) Math.round(durationS * 1000.0 / distanceM);
    }

    public static ActivityResponse from(Activity a) {
        return from(a, null, null);
    }
}
