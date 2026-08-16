package com.coachrun.service;

import com.coachrun.entity.Activity;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.RaceObjective;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.RaceObjectiveRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Le bilan de la semaine — ce que le produit n'avait jamais su dire.
 *
 * <p><b>Le manque.</b> Vingt-sept types de notification, et pas un bilan. Tout était
 * événementiel : une séance déposée, un retour reçu, une alerte levée. Rien ne disait
 * <i>« voilà ce que tu as fait »</i>, alors que c'est précisément la phrase qu'un athlète attend
 * le dimanche soir et qu'un coach cherche le lundi matin.</p>
 *
 * <p><b>Deux bilans, deux questions différentes.</b> Celui de l'athlète répond à « ma semaine
 * a-t-elle ressemblé à ce qui était prévu ? » ; celui du coach à « où en est mon groupe, et sur
 * qui dois-je me pencher cette semaine ? ». Ils ne partagent que la fenêtre de calcul.</p>
 *
 * <p><b>Aucune donnée de santé.</b> Ni douleur, ni blessure, ni motif de séance manquée : ces
 * bilans partent en notification et, pour le coach, par e-mail. La sensation moyenne y figure —
 * c'est une appréciation d'entraînement que l'athlète déclare pour lui-même, pas un symptôme.</p>
 */
@Service
@RequiredArgsConstructor
public class WeeklyRecapService {

    private final WorkoutRepository workoutRepository;
    private final ActivityRepository activityRepository;
    private final RaceObjectiveRepository raceRepository;

    /**
     * Le bilan d'un athlète.
     *
     * @param km              distance réellement parcourue, en kilomètres.
     * @param done            séances menées à leur terme ou écourtées.
     * @param planned         séances qui étaient au programme.
     * @param feel            sensation moyenne déclarée (1 = excellente … 5 = très mauvaise), ou null.
     * @param feelPrevious    la même, la semaine d'avant — de quoi dire une tendance, ou null.
     */
    public record AthleteRecap(double km, int done, int planned, Double feel, Double feelPrevious) {

        /** Une semaine sans rien : ni séance prévue, ni kilomètre couru. Rien à raconter. */
        public boolean isEmpty() {
            return planned == 0 && km <= 0;
        }

        /**
         * La sensation s'améliore-t-elle ?
         *
         * <p>Attention au sens de l'échelle : <b>1 est la meilleure sensation</b>, donc une
         * moyenne qui <i>baisse</i> est une amélioration. Le seuil de 0,3 point évite d'annoncer
         * une tendance là où deux séances suffisent à faire bouger la moyenne.</p>
         *
         * @return 1 si ça s'améliore, -1 si ça se dégrade, 0 si stable ou indécidable.
         */
        public int feelTrend() {
            if (feel == null || feelPrevious == null) {
                return 0;
            }
            double delta = feelPrevious - feel;
            if (delta >= 0.3) {
                return 1;
            }
            return delta <= -0.3 ? -1 : 0;
        }
    }

    /**
     * Le bilan d'un coach sur son périmètre.
     *
     * @param athletes         athlètes suivis, qu'ils aient eu une séance ou non.
     * @param done             séances réalisées (terminées ou écourtées).
     * @param planned          séances au programme.
     * @param toWatch          athlètes en vigilance, tels que le tableau de bord les compte.
     * @param upcomingRaces    courses dans les quinze jours.
     */
    public record CoachRecap(int athletes, int done, int planned, int toWatch, int upcomingRaces) {

        /** Sans une seule séance prévue sur la semaine, il n'y a pas de bilan à envoyer. */
        public boolean isEmpty() {
            return planned == 0 && upcomingRaces == 0;
        }

        /** Taux de réalisation, en pourcentage entier. 0 quand rien n'était prévu. */
        public int completionPct() {
            return planned == 0 ? 0 : (int) Math.round(done * 100.0 / planned);
        }
    }

    // --- Athlète ----------------------------------------------------------------

    /** Bilan d'un athlète sur la semaine {@code [from, to]}, bornes incluses. */
    @Transactional(readOnly = true)
    public AthleteRecap forAthlete(UUID athleteId, LocalDate from, LocalDate to) {
        List<UUID> ids = List.of(athleteId);
        List<Workout> week = workoutRepository.findByAthleteIdInAndScheduledDateBetween(ids, from, to);

        return new AthleteRecap(
                kilometres(ids, from, to),
                (int) week.stream().filter(WeeklyRecapService::realized).count(),
                // Une séance annulée reste une séance qui était prévue : elle compte au
                // dénominateur, sans quoi « 3 sur 3 » récompenserait celui qui n'a rien fait.
                week.size(),
                averageFeel(week),
                averageFeel(workoutRepository.findByAthleteIdInAndScheduledDateBetween(
                        ids, from.minusWeeks(1), to.minusWeeks(1))));
    }

    // --- Coach ------------------------------------------------------------------

    /**
     * Bilan d'un coach sur les athlètes qu'il suit.
     *
     * @param toWatch nombre d'athlètes en vigilance, calculé par l'appelant à partir des alertes
     *                du tableau de bord — ce service ne réimplémente pas la détection.
     */
    @Transactional(readOnly = true)
    public CoachRecap forCoach(Collection<Athlete> athletes, LocalDate from, LocalDate to, int toWatch) {
        if (athletes.isEmpty()) {
            return new CoachRecap(0, 0, 0, 0, 0);
        }
        List<UUID> ids = athletes.stream().map(Athlete::getId).toList();
        List<Workout> week = workoutRepository.findByAthleteIdInAndScheduledDateBetween(ids, from, to);

        // Quinze jours : de quoi voir venir un affûtage et une logistique de déplacement, sans
        // annoncer chaque lundi une course qui reste à deux mois.
        List<RaceObjective> races = raceRepository.findByAthleteIdInAndRaceDateBetween(
                ids, to.plusDays(1), to.plusDays(15));

        return new CoachRecap(
                athletes.size(),
                (int) week.stream().filter(WeeklyRecapService::realized).count(),
                week.size(),
                toWatch,
                races.size());
    }

    // --- Calculs communs --------------------------------------------------------

    /**
     * Une séance « réalisée » : menée à son terme ou écourtée.
     *
     * <p>Compter l'écourtée comme faite est un choix : le taux de réalisation répond à « l'athlète
     * s'est-il entraîné ? », pas à « a-t-il fait exactement ce qui était écrit ? ». Une sortie
     * longue arrêtée à mi-parcours reste une sortie, et la traiter comme une séance manquée
     * découragerait précisément la déclaration honnête que le produit cherche à obtenir.</p>
     */
    private static boolean realized(Workout w) {
        return w.getStatus() == WorkoutStatus.COMPLETED || w.getStatus() == WorkoutStatus.PARTIAL;
    }

    /**
     * Kilomètres réellement parcourus, lus sur les <b>activités</b> et non sur les séances.
     *
     * <p>Une séance porte une distance <i>cible</i> ; seule l'activité — importée de la montre ou
     * saisie — porte ce qui a été couru. Annoncer la cible dans un bilan reviendrait à féliciter
     * l'athlète pour ce qu'on lui avait demandé.</p>
     */
    private double kilometres(List<UUID> athleteIds, LocalDate from, LocalDate to) {
        double metres = 0;
        for (Activity a : activityRepository.findByAthleteIdInAndActivityDateBetween(athleteIds, from, to)) {
            if (a.getDistanceM() != null) {
                metres += a.getDistanceM();
            }
        }
        return Math.round(metres / 100.0) / 10.0;
    }

    /** Sensation moyenne déclarée sur la semaine, ou null si l'athlète n'en a déclaré aucune. */
    private static Double averageFeel(List<Workout> workouts) {
        return workouts.stream()
                .map(Workout::getFeel)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average().stream().boxed().findFirst().orElse(null);
    }
}
