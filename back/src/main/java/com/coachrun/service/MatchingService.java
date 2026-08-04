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

    /**
     * Statut résultant d'un rapprochement, selon l'écart au prévu.
     *
     * <p>Sans cible mesurable, le statut retenu était {@code COMPLETED} : n'importe quelle activité
     * du jour validait donc une séance dont on ne pouvait rien vérifier. On retient désormais
     * {@code PARTIAL} — la sortie a bien eu lieu, mais rien ne prouve qu'elle correspond à la
     * séance prescrite, et c'est à l'athlète ou au coach de trancher.</p>
     *
     * <p><b>Ce que ce calcul ne dit pas.</b> Il compare des volumes, pas des structures : un
     * 10 × 400 m remplacé par un footing de même distance ressort « réalisé ». Comparer la
     * structure demanderait le découpage réel de l'activité, que l'import ne fournit pas
     * aujourd'hui ; en attendant, le ressenti de l'athlète reste la seule source qui distingue
     * les deux.</p>
     */
    public WorkoutStatus resolvedStatus(Activity activity, Workout workout) {
        Integer target = workout.getTargetDistanceM();
        Integer actual = activity.getDistanceM();
        if (target == null || target == 0 || actual == null) {
            return WorkoutStatus.PARTIAL;
        }
        double ratio = Math.abs(actual - target) / (double) target;
        return ratio <= COMPLETED_DISTANCE_TOLERANCE ? WorkoutStatus.COMPLETED : WorkoutStatus.PARTIAL;
    }

    /**
     * Score de rapprochement ∈ [0,1] : proximité de date, de distance <strong>et</strong> de durée.
     *
     * <p>Sans la durée, une sortie de 10 km en 40 min et une séance prévue de 10 km en 60 min
     * obtenaient un score parfait — alors que ce sont deux séances différentes. Le poids se
     * répartit sur les critères effectivement comparables : une séance sans durée cible reste
     * rapprochable sur la date et la distance, comme avant.</p>
     */
    private double score(Activity activity, Workout workout) {
        long dayGap = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                workout.getScheduledDate(), activity.getActivityDate()));
        double dateScore = switch ((int) Math.min(dayGap, 2)) {
            case 0 -> 1.0;
            case 1 -> 0.6;
            default -> 0.0;
        };

        Double distScore = closeness(workout.getTargetDistanceM(), activity.getDistanceM());
        Double durationScore = closeness(workout.getTargetDurationS(), activity.getDurationS());
        if (distScore == null && durationScore == null) {
            // Rien de comparable : la date seule ne prouve rien. Se fier à elle rapprochait
            // automatiquement n'importe quelle sortie du jour de n'importe quelle séance sans
            // cible — et la validait au passage. On laisse le rapprochement à la main.
            return 0.0;
        }

        // La date pèse la moitié ; le reste se partage entre les mesures disponibles.
        double effortScore = distScore == null ? durationScore
                : durationScore == null ? distScore
                : (distScore + durationScore) / 2.0;
        return 0.5 * dateScore + 0.5 * effortScore;
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
