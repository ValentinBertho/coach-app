package com.coachrun;

import com.coachrun.entity.Activity;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.ActivitySport;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.service.MatchingService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests unitaires de l'algorithme de rapprochement (sans Spring). */
class MatchingServiceTest {

    private final MatchingService matching = new MatchingService();

    private Workout workout(LocalDate date, Integer targetM, WorkoutStatus status) {
        Workout w = new Workout();
        w.setScheduledDate(date);
        w.setTargetDistanceM(targetM);
        w.setStatus(status);
        return w;
    }

    private Activity activity(LocalDate date, Integer distanceM) {
        Activity a = new Activity();
        a.setActivityDate(date);
        a.setDistanceM(distanceM);
        return a;
    }

    @Test
    void matchesSameDaySimilarDistance() {
        LocalDate d = LocalDate.of(2026, 7, 1);
        Activity act = activity(d, 10200);
        Workout planned = workout(d, 10000, WorkoutStatus.PLANNED);

        Optional<Workout> best = matching.findBestMatch(act, List.of(planned));
        assertThat(best).contains(planned);
        assertThat(matching.resolvedStatus(act, planned)).isEqualTo(WorkoutStatus.COMPLETED);
    }

    @Test
    void partialWhenDistanceFarFromTarget() {
        LocalDate d = LocalDate.of(2026, 7, 1);
        Activity act = activity(d, 6000); // -40% vs 10 km prévu
        Workout planned = workout(d, 10000, WorkoutStatus.PLANNED);
        assertThat(matching.resolvedStatus(act, planned)).isEqualTo(WorkoutStatus.PARTIAL);
    }

    @Test
    void noMatchWhenTooFarInTime() {
        Activity act = activity(LocalDate.of(2026, 7, 10), 10000);
        Workout planned = workout(LocalDate.of(2026, 7, 1), 10000, WorkoutStatus.PLANNED);
        assertThat(matching.findBestMatch(act, List.of(planned))).isEmpty();
    }

    /**
     * L'ordre réel des choses : on court, on note sa séance en rentrant — elle passe donc
     * {@code COMPLETED} — et la montre se synchronise après. Ne retenir que les séances
     * {@code PLANNED} rendait la séance introuvable pour la sortie qui arrivait derrière : la
     * sortie restait « à rattacher » et la séance sans réalisé.
     */
    @Test
    void matchesAWorkoutTheAthleteHasAlreadyReported() {
        LocalDate d = LocalDate.of(2026, 7, 1);
        Workout completed = workout(d, 10000, WorkoutStatus.COMPLETED);
        assertThat(matching.findBestMatch(activity(d, 10000), List.of(completed))).contains(completed);
    }

    /**
     * Une séance déclarée non faite, en revanche, reste hors d'atteinte : l'athlète a dit ne pas
     * l'avoir courue, et une sortie du même jour ne doit pas le contredire.
     */
    @Test
    void ignoresMissedWorkouts() {
        LocalDate d = LocalDate.of(2026, 7, 1);
        Workout missed = workout(d, 10000, WorkoutStatus.MISSED);
        assertThat(matching.findBestMatch(activity(d, 10000), List.of(missed))).isEmpty();
    }

    // --- La durée compte, elle aussi ------------------------------------------------------

    private Workout workout(LocalDate date, Integer targetM, Integer targetS, WorkoutStatus status) {
        Workout w = workout(date, targetM, status);
        w.setTargetDurationS(targetS);
        return w;
    }

    private Activity activity(LocalDate date, Integer distanceM, Integer durationS) {
        Activity a = activity(date, distanceM);
        a.setDurationS(durationS);
        return a;
    }

    /**
     * 10 km en 40 min contre 10 km prévus en 60 min : la distance colle, l'effort n'a rien à voir.
     * Avec la seule distance, ces deux séances obtenaient un score parfait.
     */
    @Test
    void durationSeparatesTwoSessionsOfTheSameDistance() {
        LocalDate d = LocalDate.of(2026, 7, 1);
        Activity fast = activity(d, 10000, 40 * 60);
        Workout easyRun = workout(d, 10000, 60 * 60, WorkoutStatus.PLANNED);
        Workout tempo = workout(d, 10000, 42 * 60, WorkoutStatus.PLANNED);

        assertThat(matching.findBestMatch(fast, List.of(easyRun, tempo))).contains(tempo);
    }

    /** Une séance sans durée cible reste rapprochable sur la date et la distance, comme avant. */
    @Test
    void stillMatchesWhenTheWorkoutHasNoTargetDuration() {
        LocalDate d = LocalDate.of(2026, 7, 1);
        Workout planned = workout(d, 10000, null, WorkoutStatus.PLANNED);
        assertThat(matching.findBestMatch(activity(d, 10200, 50 * 60), List.of(planned)))
                .contains(planned);
    }

    /** Écart de durée massif le même jour : le rapprochement automatique ne se déclenche plus. */
    @Test
    void doesNotAutoMatchWhenDurationsAreWildlyApart() {
        LocalDate d = LocalDate.of(2026, 7, 1);
        Activity sprint = activity(d, 3000, 12 * 60);
        Workout longRun = workout(d, 25000, 150 * 60, WorkoutStatus.PLANNED);

        assertThat(matching.findBestMatch(sprint, List.of(longRun))).isEmpty();
    }

    // --- On ne peut pas avoir déjà fait ce qui n'a pas encore eu lieu ----------------------

    /**
     * Une séance prévue demain n'est jamais candidate, même parfaitement ressemblante.
     *
     * <p>La proximité de date était symétrique : un jour d'écart ne coûtait que deux dixièmes du
     * score final, qu'un volume presque identique récupérait largement. La séance du lendemain
     * gagnait donc contre celle du jour même, et se retrouvait déclarée faite avant d'avoir été
     * courue.</p>
     */
    @Test
    void neverMatchesAWorkoutPlannedAfterTheActivity() {
        LocalDate today = LocalDate.of(2026, 8, 10);
        Activity ran = activity(today, 11350, 3465);
        Workout tomorrow = workout(today.plusDays(1), 11320, 3450, WorkoutStatus.PLANNED);

        assertThat(matching.findBestMatch(ran, List.of(tomorrow))).isEmpty();
    }

    /**
     * Le cas exact remonté : la séance du jour a des cibles inexploitables — une prescription
     * écrite en durée dont aucun bloc n'est chiffrable ne totalise que ses éducatifs — pendant que
     * celle du lendemain colle au mètre près. Aucune des deux ne gagne au score : celle de demain
     * est écartée d'office, celle du jour n'a rien à opposer. C'est le repli qui la récupère.
     */
    @Test
    void neitherWinsOnScoreWhenTodaysTargetsAreUnusable() {
        LocalDate today = LocalDate.of(2026, 8, 10);
        Activity ran = activity(today, 11350, 3465);
        Workout todaysSession = workout(today, 100, null, WorkoutStatus.PLANNED);
        Workout tomorrow = workout(today.plusDays(1), 11320, 3450, WorkoutStatus.PLANNED);

        assertThat(matching.findBestMatch(ran, List.of(todaysSession, tomorrow))).isEmpty();
        assertThat(matching.canFallBackOn(ran, todaysSession)).isTrue();
        assertThat(matching.resolvedStatus(ran, todaysSession)).isEqualTo(WorkoutStatus.PARTIAL);
    }

    // --- Repli : la seule séance de la journée, quand rien ne permet de la comparer ----------

    /**
     * Une séance sans volume exploitable n'a rien à opposer à la sortie du jour. Refuser tout
     * rapprochement laissait deux écrans vides — la sortie orpheline, la séance sans réalisé —
     * pour un entraînement qui a bel et bien eu lieu.
     *
     * <p>La garde « une seule séance ce jour-là » n'est pas testée ici : elle demande de connaître
     * la journée entière, séances déjà rapprochées comprises, et vit donc dans le service qui
     * appelle ce prédicat.</p>
     */
    @Test
    void allowsTheFallbackWhenNothingIsComparable() {
        LocalDate d = LocalDate.of(2026, 8, 10);
        Activity ran = activity(d, 11350, 3465);
        Workout noTargets = workout(d, null, null, WorkoutStatus.PLANNED);

        assertThat(matching.canFallBackOn(ran, noTargets)).isTrue();
        assertThat(matching.resolvedStatus(ran, noTargets)).isEqualTo(WorkoutStatus.PARTIAL);
    }

    /**
     * Le repli ne vaut que pour le jour même : rattacher la sortie d'aujourd'hui à la séance
     * d'hier sans rien pour l'étayer serait exactement le rapprochement sur date qu'on a retiré.
     */
    @Test
    void theFallbackNeverReachesBackToYesterday() {
        LocalDate d = LocalDate.of(2026, 8, 10);
        Activity ran = activity(d, 11350, 3465);
        Workout yesterday = workout(d.minusDays(1), null, null, WorkoutStatus.PLANNED);

        assertThat(matching.canFallBackOn(ran, yesterday)).isFalse();
    }

    /** Une séance déclarée non faite reste hors d'atteinte, y compris par le repli. */
    @Test
    void theFallbackNeverContradictsAMissedSession() {
        LocalDate d = LocalDate.of(2026, 8, 10);
        Activity ran = activity(d, 11350, 3465);
        Workout missed = workout(d, null, null, WorkoutStatus.MISSED);

        assertThat(matching.canFallBackOn(ran, missed)).isFalse();
    }

    /**
     * Deux volumes qui se contredisent restent un refus : ils portent une information, là où
     * l'absence de cible n'en porte aucune.
     */
    @Test
    void theFallbackDoesNotOverrideAContradictoryVolume() {
        LocalDate d = LocalDate.of(2026, 8, 10);
        Activity sprint = activity(d, 3000, 12 * 60);
        Workout longRun = workout(d, 25000, 150 * 60, WorkoutStatus.PLANNED);

        assertThat(matching.findBestMatch(sprint, List.of(longRun))).isEmpty();
        assertThat(matching.canFallBackOn(sprint, longRun)).isFalse();
    }

    /** La séance de la veille, elle, reste rapprochable : on a couru en retard, ça arrive. */
    @Test
    void stillMatchesYesterdaysWorkout() {
        LocalDate today = LocalDate.of(2026, 8, 10);
        Activity ran = activity(today, 11350, 3465);
        Workout yesterday = workout(today.minusDays(1), 11320, 3450, WorkoutStatus.PLANNED);

        assertThat(matching.findBestMatch(ran, List.of(yesterday))).contains(yesterday);
    }

    // --- Le sport : ce qui distingue une journée à cinq sorties -----------------------------
    //
    // La journée rapportée en bêta : un fractionné prescrit « Endurance · Séance 8x(200/400) »
    // de 13,3 km / 59 min, et cinq sorties le même jour — une sortie gravel, une séance de
    // renforcement, deux footings et le fractionné. C'est la MUSCULATION qui a emporté la
    // séance : 29 min contre 59 prévues, distance nulle lue comme « non renseignée », score
    // 0,75 — devant le fractionné réellement couru, dont la montre n'avait enregistré que la
    // partie rapide (6,2 km) et qui plafonnait à 0,72.

    private Workout course(LocalDate date, String title, Integer targetM, Integer targetS) {
        Workout w = workout(date, targetM, targetS, WorkoutStatus.PLANNED);
        w.setType(WorkoutType.INTERVALS);
        w.setTitle(title);
        return w;
    }

    private Activity sortie(LocalDate date, String title, ActivitySport sport,
                            Integer distanceM, Integer durationS) {
        Activity a = activity(date, distanceM, durationS);
        a.setTitle(title);
        a.setSport(sport);
        return a;
    }

    @Test
    void aWeightSessionNeverRealisesARunningWorkout() {
        LocalDate d = LocalDate.of(2026, 9, 8);
        Workout prescribed = course(d, "Endurance · Séance 8x(200/400)", 13300, 59 * 60);
        Activity weights = sortie(d, "Entraînement aux poids le midi",
                ActivitySport.STRENGTH, 0, 29 * 60);

        assertThat(matching.findBestMatch(weights, List.of(prescribed))).isEmpty();
        assertThat(matching.canFallBackOn(weights, prescribed)).isFalse();
    }

    @Test
    void aBikeRideNeverRealisesARunningWorkout() {
        LocalDate d = LocalDate.of(2026, 9, 8);
        Workout prescribed = course(d, "Endurance · Séance 8x(200/400)", 13300, 59 * 60);
        Activity gravel = sortie(d, "Afternoon Gravel Ride", ActivitySport.RIDE, 11520, 32 * 60);

        assertThat(matching.findBestMatch(gravel, List.of(prescribed))).isEmpty();
    }

    /**
     * Le sport non déclaré ne bloque rien : une saisie manuelle et tout l'historique importé
     * avant l'arrivée de la colonne doivent continuer de se rapprocher comme avant.
     */
    @Test
    void anUndeclaredSportStillMatches() {
        LocalDate d = LocalDate.of(2026, 9, 8);
        Workout prescribed = course(d, "Séance du jour", 10000, 50 * 60);
        Activity unknown = sortie(d, "Sortie", null, 10200, 51 * 60);

        assertThat(matching.findBestMatch(unknown, List.of(prescribed))).contains(prescribed);
    }

    /** Une séance de renforcement, elle, attend bien de la musculation — pas un footing. */
    @Test
    void aStrengthWorkoutTakesTheStrengthActivity() {
        LocalDate d = LocalDate.of(2026, 9, 8);
        Workout gym = workout(d, null, 30 * 60, WorkoutStatus.PLANNED);
        gym.setType(WorkoutType.STRENGTH);
        Activity weights = sortie(d, "Renfo", ActivitySport.STRENGTH, 0, 29 * 60);
        Activity run = sortie(d, "Footing", ActivitySport.RUN, 8000, 40 * 60);

        assertThat(matching.findBestMatch(weights, List.of(gym))).contains(gym);
        assertThat(matching.findBestMatch(run, List.of(gym))).isEmpty();
    }

    /** Un jour de repos ne se « réalise » pas : aucune sortie ne le valide. */
    @Test
    void aRestDayIsNeverMatched() {
        LocalDate d = LocalDate.of(2026, 9, 8);
        Workout rest = workout(d, null, null, WorkoutStatus.PLANNED);
        rest.setType(WorkoutType.REST);
        Activity run = sortie(d, "Footing", ActivitySport.RUN, 8000, 40 * 60);

        assertThat(matching.findBestMatch(run, List.of(rest))).isEmpty();
        assertThat(matching.canFallBackOn(run, rest)).isFalse();
    }

    // --- Le titre : ce que l'athlète a écrit lui-même ---------------------------------------

    /**
     * « 8*(200/400) » en face de « Endurance · Séance 8x(200/400) ». Les volumes désignaient le
     * footing d'échauffement — enregistré à part, 4,8 km — plutôt que le fractionné, dont la
     * montre n'avait gardé que la partie rapide. Le titre, lui, ne laisse aucun doute.
     */
    @Test
    void theTitleWrittenByTheAthleteDecidesBetweenTwoRunsOfTheSameDay() {
        LocalDate d = LocalDate.of(2026, 9, 8);
        Workout prescribed = course(d, "Endurance · Séance 8x(200/400)", 13300, 59 * 60);
        Activity warmupRun = sortie(d, "Course à pied en soirée", ActivitySport.RUN, 4810, 23 * 60);
        Activity intervals = sortie(d, "8*(200/400)", ActivitySport.RUN, 6220, 25 * 60);

        assertThat(matching.confidence(intervals, prescribed))
                .isGreaterThan(matching.confidence(warmupRun, prescribed));
        assertThat(matching.findBestMatch(intervals, List.of(prescribed))).contains(prescribed);
    }

    /**
     * Les noms composés par Strava sont écartés : « Afternoon Run » ressemblerait à toutes les
     * séances de course de la semaine, et la prime deviendrait du bruit.
     */
    @Test
    void stravaAutoNamesEarnNoTitleBonus() {
        LocalDate d = LocalDate.of(2026, 9, 8);
        Workout prescribed = course(d, "Morning Run 8x400", 10000, 50 * 60);
        // Trois sorties identiques au mètre et à la seconde près : seul leur titre les sépare.
        Activity auto = sortie(d, "Morning Run", ActivitySport.RUN, 10000, 50 * 60);
        Activity nameless = sortie(d, null, ActivitySport.RUN, 10000, 50 * 60);
        Activity written = sortie(d, "8x400 en négatif", ActivitySport.RUN, 10000, 50 * 60);

        // « Morning Run » recouvre pourtant la moitié du titre de la séance : reconnu comme un
        // nom composé par Strava, il ne vaut pas mieux qu'une sortie sans titre du tout.
        assertThat(matching.confidence(auto, prescribed))
                .isEqualTo(matching.confidence(nameless, prescribed));
        // Un titre écrit par l'athlète, lui, désigne la séance — et le score le dit.
        assertThat(matching.confidence(written, prescribed))
                .isGreaterThan(matching.confidence(auto, prescribed));
    }

    /** Un seul mot commun sans chiffre ne prouve rien : « long » désigne la moitié du calendrier. */
    @Test
    void oneCommonWordIsNotEnough() {
        LocalDate d = LocalDate.of(2026, 9, 8);
        Workout prescribed = course(d, "Sortie longue vallonnée", 20000, 100 * 60);
        Activity other = sortie(d, "Footing vallonné", ActivitySport.RUN, 20000, 100 * 60);
        Activity plain = sortie(d, "Zzz", ActivitySport.RUN, 20000, 100 * 60);

        assertThat(matching.confidence(other, prescribed))
                .isEqualTo(matching.confidence(plain, prescribed));
    }

    /** Une séance interdite par le sport vaut zéro, pas « un peu moins » : c'est un refus. */
    @Test
    void confidenceIsZeroForAnIneligibleWorkout() {
        LocalDate d = LocalDate.of(2026, 9, 8);
        Workout prescribed = course(d, "Séance", 13300, 59 * 60);
        Activity weights = sortie(d, "Renfo", ActivitySport.STRENGTH, 0, 29 * 60);

        assertThat(matching.confidence(weights, prescribed)).isZero();
    }

    /** Une sortie sans le moindre mètre ne réalise pas une séance chiffrée en kilomètres. */
    @Test
    void aZeroDistanceContradictsADistanceTarget() {
        LocalDate d = LocalDate.of(2026, 9, 8);
        Workout prescribed = course(d, "Séance", 13300, 59 * 60);
        // Sport non déclaré : c'est bien la distance nulle, et elle seule, qui doit refuser.
        Activity indoor = sortie(d, "Tapis ?", null, 0, 29 * 60);

        assertThat(matching.findBestMatch(indoor, List.of(prescribed))).isEmpty();
    }
}
