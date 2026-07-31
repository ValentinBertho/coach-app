package com.coachrun.dto.response;

import com.coachrun.entity.Activity;
import com.coachrun.entity.Workout;

import java.util.UUID;

/**
 * Invitation à confirmer le ressenti d'une séance rapprochée d'une activité importée.
 *
 * <p>Le rapprochement Strava sait déjà ce qui a été couru — distance, durée, fréquence
 * cardiaque. Il ne sait rien de ce que l'athlète a ressenti, or c'est ce ressenti qui alimente
 * la forme. On lui rend donc les faits pré-remplis pour qu'il n'ait plus qu'à confirmer et
 * poser sa fatigue et sa douleur : deux curseurs au lieu d'un formulaire.</p>
 */
public record FeedbackPromptResponse(
        UUID activityId,
        String source,
        Integer distanceM,
        Integer durationS,
        Integer avgHr,
        Integer elevationGainM,
        WorkoutResponse workout
) {

    public static FeedbackPromptResponse of(Activity a, Workout w) {
        return new FeedbackPromptResponse(a.getId(), a.getSource().name(), a.getDistanceM(),
                a.getDurationS(), a.getAvgHr(), a.getElevationGainM(), WorkoutResponse.from(w));
    }
}
