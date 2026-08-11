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
        Integer durationDeltaS,
        Integer rpe,
        /** Sensation générale (1 = excellente … 5 = très mauvaise). */
        Integer feel,
        /** Fatigue et douleur ressenties (0–10) — mêmes échelles que sur une séance. */
        Integer fatigue,
        Integer pain,
        /** Blessures déclarées ; liste vide si aucune. */
        java.util.List<com.coachrun.dto.InjuryReport> injuries,
        String athleteComment) {

    /**
     * Sortie + son ressenti effectif.
     *
     * <p>Une sortie rapprochée n'a pas de ressenti à elle : c'est sa séance qui le porte, et
     * c'est celui-là qui est rendu. Passer {@code null} en {@code debriefOwner} fait rendre le
     * ressenti de la sortie elle-même — le cas d'une sortie libre, et celui d'une séance qui
     * n'a pas encore été débriefée.</p>
     */
    public static ActivityResponse of(Activity a, com.coachrun.entity.Workout debriefOwner,
                                      Integer distanceDeltaM, Integer durationDeltaS) {
        Integer rpe = debriefOwner != null ? debriefOwner.getRpe() : a.getRpe();
        Integer feel = debriefOwner != null ? debriefOwner.getFeel() : a.getFeel();
        Integer fatigue = debriefOwner != null ? debriefOwner.getFatigue() : a.getFatigue();
        Integer pain = debriefOwner != null ? debriefOwner.getPain() : a.getPain();
        String injuriesJson = debriefOwner != null ? debriefOwner.getInjuriesJson() : a.getInjuriesJson();
        String comment = debriefOwner != null ? debriefOwner.getAthleteComment() : a.getAthleteComment();
        return new ActivityResponse(
                a.getId(), a.getAthlete().getId(), a.getSource(), a.getActivityDate(), a.getTitle(),
                a.getDistanceM(), a.getDurationS(), a.getAvgHr(), a.getElevationGainM(),
                a.getMaxHr(), a.getAvgCadence(), a.getAvgPowerW(), a.getCalories(),
                pace(a.getDistanceM(), a.getDurationS()),
                a.getStatus(), a.getMatchedWorkoutId(), distanceDeltaM, durationDeltaS,
                rpe, feel, fatigue, pain,
                com.coachrun.util.InjuryCodec.read(injuriesJson), comment);
    }

    public static ActivityResponse from(Activity a, Integer distanceDeltaM, Integer durationDeltaS) {
        return of(a, null, distanceDeltaM, durationDeltaS);
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
