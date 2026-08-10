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

    /**
     * Meilleure séance correspondant à l'activité, si le score dépasse le seuil.
     *
     * <h2>Pourquoi une séance déjà notée reste rapprochable</h2>
     *
     * <p>Le tri ne retenait que les séances {@code PLANNED}. Or l'ordre naturel des choses est
     * l'inverse : on court, on note sa séance dans l'application en rentrant — elle passe donc
     * {@code COMPLETED} — <b>et la montre se synchronise après</b>. À l'arrivée, la sortie ne
     * trouvait plus aucune séance à qui se rattacher : elle restait « à rattacher », la séance
     * restait sans réalisé, et la comparaison prévu/réalisé n'existait pour ni l'un ni l'autre.
     * C'est le « je ne retrouve plus les séances une fois qu'elles ont été faites » remonté en
     * bêta, et le webhook — qui accélère la synchro sans changer son rang dans la séquence — ne
     * l'aurait pas corrigé.</p>
     *
     * <p>Une séance {@code MISSED} reste écartée, elle : l'athlète a déclaré ne pas l'avoir faite,
     * et une sortie du même jour ne doit pas contredire cette déclaration. C'est ce qui distingue
     * les deux cas — l'un complète une déclaration, l'autre la nierait.</p>
     *
     * <h2>Pourquoi une séance de demain n'est jamais candidate</h2>
     *
     * <p>La proximité de date était symétrique : une séance de la veille et une séance du
     * lendemain valaient le même demi-point. Or les deux situations n'ont rien à voir. Rattacher
     * la sortie d'aujourd'hui à la séance d'hier décrit un fait ordinaire — on a couru en retard,
     * ou la montre a basculé après minuit. La rattacher à la séance de <b>demain</b> affirme qu'on
     * a déjà fait ce qui n'a pas encore eu lieu.</p>
     *
     * <p>Et l'écart de date se rattrapait trop facilement : un jour d'écart ne coûte que deux
     * dixièmes du score final, qu'un volume presque identique récupère largement. Une sortie de
     * 11,35 km en 57:45 se rapprochait donc de la séance du lendemain prévue à 11,32 km en 57:30
     * (score 0,80) plutôt que de celle du jour même dont les cibles étaient mal renseignées
     * (0,50) : la séance du jour restait sans réalisé, et celle du lendemain était déclarée faite
     * avant d'avoir été courue — avec, dans la foulée, un « Repos demain » le soir venu.</p>
     *
     * <p>Le rapprochement <b>manuel</b> reste possible dans les deux sens : l'athlète qui avance
     * réellement sa séance du samedi au vendredi la rattache lui-même. C'est un geste délibéré, ce
     * que l'automatisme ne doit pas faire à sa place.</p>
     *
     * @param candidates séances de la fenêtre, <b>déjà débarrassées</b> de celles qui portent une
     *                   activité : le tri ici ne sait pas ce qui est déjà rapproché
     */
    public Optional<Workout> findBestMatch(Activity activity, List<Workout> candidates) {
        return candidates.stream()
                .filter(w -> w.getStatus() != WorkoutStatus.MISSED)
                .filter(w -> !isAfterActivity(w, activity))
                .map(w -> new Scored(w, score(activity, w)))
                .filter(s -> s.score >= MATCH_THRESHOLD)
                .max((a, b) -> Double.compare(a.score, b.score))
                .map(s -> s.workout);
    }

    /** La séance est-elle prévue après la sortie ? On ne peut pas avoir déjà fait ce qui vient. */
    private boolean isAfterActivity(Workout workout, Activity activity) {
        return workout.getScheduledDate() != null && activity.getActivityDate() != null
                && workout.getScheduledDate().isAfter(activity.getActivityDate());
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
        // Écart en jours, toujours positif : les séances postérieures à la sortie sont écartées
        // en amont par findBestMatch, ce score ne juge donc que le passé et le jour même.
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
