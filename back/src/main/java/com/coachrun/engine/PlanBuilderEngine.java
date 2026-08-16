package com.coachrun.engine;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * La trame d'un plan construite <b>depuis la date de la course</b>, et non depuis « la semaine
 * prochaine ».
 *
 * <h2>Le manque qu'il comble</h2>
 *
 * <p>{@code WorkoutService#generateMesocycle} recopie une semaine type en multipliant les volumes
 * par un facteur. C'est utile, mais ça ignore la date de course, les indisponibilités déjà
 * déclarées et la charge actuelle de l'athlète — et ça démarre toujours la semaine suivante. Le
 * geste fondateur du métier (« 14 semaines jusqu'à Paris : 4 de reprise, 6 de spécifique, 2 de
 * volume, 2 d'affûtage ») n'existait nulle part.</p>
 *
 * <h2>Ce que ce moteur produit — et ce qu'il ne produit pas</h2>
 *
 * <p>Une trame : une ligne par semaine, avec sa phase, son facteur de volume et ce qui la
 * caractérise. <b>Il ne crée aucune séance.</b> La matérialisation est un geste séparé, déclenché
 * par le coach après avoir vu l'aperçu — la machine pose la structure, le coach garde chaque
 * séance.</p>
 *
 * <p>Logique pure, testable sans contexte Spring.</p>
 */
@Component
public class PlanBuilderEngine {

    /** Phase d'un mésocycle, dans l'ordre où elles s'enchaînent. */
    public enum Phase {
        /** Remise en route : volume modéré, peu d'intensité. */
        BASE,
        /** Développement : le volume monte. */
        BUILD,
        /** Spécifique : allure de course, volume haut mais stable. */
        SPECIFIC,
        /** Décharge : une semaine plus basse pour assimiler. */
        DELOAD,
        /** Affûtage : le volume tombe, l'intensité reste. */
        TAPER,
        /** La semaine de la course. */
        RACE
    }

    /**
     * @param index       numéro de semaine, à partir de 1
     * @param monday      lundi de la semaine
     * @param phase       phase de la semaine
     * @param volumeFactor facteur appliqué au volume de la semaine de référence
     * @param skipped     vrai si la semaine tombe dans une indisponibilité déclarée : elle reste
     *                    dans la trame (le coach doit la voir) mais ne recevra aucune séance
     * @param label       ce qui la caractérise, en trois mots
     */
    public record PlanWeek(int index, LocalDate monday, Phase phase,
                           double volumeFactor, boolean skipped, String label) {
    }

    /**
     * @param weeks       la trame, semaine par semaine
     * @param raceMonday  lundi de la semaine de course
     * @param summary     la phrase qui résume la trame au coach avant qu'il ne la pose
     */
    public record Plan(List<PlanWeek> weeks, LocalDate raceMonday, String summary) {
    }

    /** Au-delà, ce n'est plus une préparation, c'est une saison : on plafonne. */
    public static final int MAX_WEEKS = 24;

    /** Semaines d'affûtage par défaut — deux, la convention sur marathon comme sur 10 km. */
    public static final int DEFAULT_TAPER_WEEKS = 2;

    /** Une semaine de décharge toutes les quatre semaines de travail. */
    public static final int DEFAULT_DELOAD_EVERY = 4;

    /**
     * @param start           premier lundi du plan (typiquement le lundi suivant)
     * @param raceDate        date de la course
     * @param peakFactor      facteur de volume au sommet de la préparation (1,3 = +30 % sur la
     *                        semaine de référence)
     * @param taperWeeks      nombre de semaines d'affûtage
     * @param deloadEvery     une décharge toutes N semaines (0 pour aucune)
     * @param unavailable     périodes déclarées indisponibles : les semaines qu'elles couvrent
     *                        entièrement sont marquées, jamais remplies
     */
    public Plan build(LocalDate start, LocalDate raceDate, double peakFactor,
                      int taperWeeks, int deloadEvery, List<LocalDate[]> unavailable) {
        LocalDate firstMonday = start.with(DayOfWeek.MONDAY);
        LocalDate raceMonday = raceDate.with(DayOfWeek.MONDAY);

        int total = (int) ChronoUnit.WEEKS.between(firstMonday, raceMonday) + 1;
        if (total < 1) {
            return new Plan(List.of(), raceMonday,
                    "La course est déjà passée ou a lieu cette semaine : rien à planifier.");
        }
        total = Math.min(total, MAX_WEEKS);

        int taper = Math.max(0, Math.min(taperWeeks, Math.max(0, total - 2)));
        int workWeeks = total - taper - 1; // -1 : la semaine de course

        List<PlanWeek> weeks = new ArrayList<>();
        int buildIndex = 0;
        for (int i = 0; i < total; i++) {
            LocalDate monday = firstMonday.plusWeeks(i);
            boolean isRaceWeek = i == total - 1;
            boolean isTaper = !isRaceWeek && i >= total - 1 - taper;

            Phase phase;
            double factor;
            if (isRaceWeek) {
                phase = Phase.RACE;
                factor = 0.5;
            } else if (isTaper) {
                phase = Phase.TAPER;
                // L'affûtage descend par paliers jusqu'à 60 % la dernière semaine avant la course.
                int rank = i - (total - 1 - taper);      // 0 = première semaine d'affûtage
                factor = taperFactor(rank, taper);
            } else if (deloadEvery > 1 && buildIndex > 0 && (buildIndex + 1) % deloadEvery == 0) {
                phase = Phase.DELOAD;
                factor = 0.6;
                buildIndex++;
            } else {
                // Montée linéaire de 1,0 jusqu'au facteur de pointe, sur les semaines de travail.
                double progress = workWeeks <= 1 ? 1 : buildIndex / (double) Math.max(1, workWeeks - 1);
                factor = 1 + (peakFactor - 1) * Math.min(1, progress);
                phase = progress < 0.45 ? Phase.BASE : Phase.SPECIFIC;
                buildIndex++;
            }

            boolean skipped = coveredBy(monday, unavailable);
            weeks.add(new PlanWeek(i + 1, monday, phase, round(factor), skipped,
                    label(phase, skipped)));
        }

        return new Plan(weeks, raceMonday, summary(weeks, taper));
    }

    /** Paliers d'affûtage : 80 % puis 60 % sur deux semaines, réparti si l'affûtage est plus long. */
    private double taperFactor(int rank, int taperWeeks) {
        if (taperWeeks <= 1) {
            return 0.7;
        }
        double from = 0.85;
        double to = 0.6;
        return from + (to - from) * (rank / (double) (taperWeeks - 1));
    }

    /** La semaine est-elle entièrement couverte par une indisponibilité déclarée&nbsp;? */
    private boolean coveredBy(LocalDate monday, List<LocalDate[]> unavailable) {
        if (unavailable == null) {
            return false;
        }
        LocalDate sunday = monday.plusDays(6);
        // « Entièrement », et pas « chevauchée » : une coupure de trois jours ne doit pas effacer
        // la semaine, elle décale seulement quelques séances — ce que le coach fera lui-même.
        return unavailable.stream().anyMatch(u ->
                !monday.isBefore(u[0]) && !sunday.isAfter(u[1]));
    }

    private String label(Phase phase, boolean skipped) {
        if (skipped) {
            return "Indisponible";
        }
        return switch (phase) {
            case BASE -> "Reprise";
            case BUILD -> "Développement";
            case SPECIFIC -> "Spécifique";
            case DELOAD -> "Décharge";
            case TAPER -> "Affûtage";
            case RACE -> "Course";
        };
    }

    private String summary(List<PlanWeek> weeks, int taper) {
        long skipped = weeks.stream().filter(PlanWeek::skipped).count();
        long deload = weeks.stream().filter(w -> w.phase() == Phase.DELOAD).count();
        StringBuilder sb = new StringBuilder();
        sb.append(weeks.size()).append(" semaines jusqu'à la course");
        if (deload > 0) {
            sb.append(", dont ").append(deload).append(" de décharge");
        }
        if (taper > 0) {
            sb.append(" et ").append(taper).append(" d'affûtage");
        }
        sb.append('.');
        if (skipped > 0) {
            sb.append(' ').append(skipped)
                    .append(skipped > 1 ? " semaines seront laissées vides" : " semaine sera laissée vide")
                    .append(" (indisponibilité déclarée).");
        }
        return sb.toString();
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
