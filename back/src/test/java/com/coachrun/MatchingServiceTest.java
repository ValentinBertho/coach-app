package com.coachrun;

import com.coachrun.entity.Activity;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
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
     * celle du lendemain colle au mètre près. Le rapprochement doit s'abstenir, et surtout pas
     * valider la séance de demain.
     */
    @Test
    void prefersNothingOverTomorrowWhenTodaysTargetsAreUnusable() {
        LocalDate today = LocalDate.of(2026, 8, 10);
        Activity ran = activity(today, 11350, 3465);
        Workout todaysSession = workout(today, 100, null, WorkoutStatus.PLANNED);
        Workout tomorrow = workout(today.plusDays(1), 11320, 3450, WorkoutStatus.PLANNED);

        assertThat(matching.findBestMatch(ran, List.of(todaysSession, tomorrow))).isEmpty();
    }

    /** La séance de la veille, elle, reste rapprochable : on a couru en retard, ça arrive. */
    @Test
    void stillMatchesYesterdaysWorkout() {
        LocalDate today = LocalDate.of(2026, 8, 10);
        Activity ran = activity(today, 11350, 3465);
        Workout yesterday = workout(today.minusDays(1), 11320, 3450, WorkoutStatus.PLANNED);

        assertThat(matching.findBestMatch(ran, List.of(yesterday))).contains(yesterday);
    }
}
