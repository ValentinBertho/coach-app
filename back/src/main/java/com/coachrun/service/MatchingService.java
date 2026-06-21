package com.coachrun.service;

import com.coachrun.entity.Activity;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Rapprochement prévu/réalisé (algorithme isolé et testable, cf. Cahier-des-charges §8).
 * Score = combinaison de la proximité de date et de distance ; rapprochement automatique
 * au-dessus du seuil {@link #MATCH_THRESHOLD}.
 */
@Service
public class MatchingService {

    /** Seuil de confiance minimal pour un rapprochement automatique. */
    public static final double MATCH_THRESHOLD = 0.6;
    /** Tolérance de distance pour considérer la séance COMPLETED (sinon PARTIAL). */
    private static final double COMPLETED_DISTANCE_TOLERANCE = 0.15;

    /** Meilleure séance prévue correspondant à l'activité, si le score dépasse le seuil. */
    public Optional<Workout> findBestMatch(Activity activity, List<Workout> candidates) {
        return candidates.stream()
                .filter(w -> w.getStatus() == WorkoutStatus.PLANNED)
                .map(w -> new Scored(w, score(activity, w)))
                .filter(s -> s.score >= MATCH_THRESHOLD)
                .max((a, b) -> Double.compare(a.score, b.score))
                .map(s -> s.workout);
    }

    /** Statut résultant d'un rapprochement, selon l'écart de distance au prévu. */
    public WorkoutStatus resolvedStatus(Activity activity, Workout workout) {
        Integer target = workout.getTargetDistanceM();
        Integer actual = activity.getDistanceM();
        if (target == null || target == 0 || actual == null) {
            return WorkoutStatus.COMPLETED;
        }
        double ratio = Math.abs(actual - target) / (double) target;
        return ratio <= COMPLETED_DISTANCE_TOLERANCE ? WorkoutStatus.COMPLETED : WorkoutStatus.PARTIAL;
    }

    private double score(Activity activity, Workout workout) {
        long dayGap = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                workout.getScheduledDate(), activity.getActivityDate()));
        double dateScore = switch ((int) Math.min(dayGap, 2)) {
            case 0 -> 1.0;
            case 1 -> 0.6;
            default -> 0.0;
        };

        Double distScore = closeness(workout.getTargetDistanceM(), activity.getDistanceM());
        if (distScore == null) {
            return dateScore; // pas de distance comparable : on se fie à la date
        }
        return 0.5 * dateScore + 0.5 * distScore;
    }

    /** Ratio de proximité min/max ∈ [0,1], ou null si non comparable. */
    private Double closeness(Integer a, Integer b) {
        if (a == null || b == null || a == 0 || b == 0) {
            return null;
        }
        return (double) Math.min(a, b) / Math.max(a, b);
    }

    private record Scored(Workout workout, double score) {
    }
}
