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

    @Test
    void ignoresNonPlannedWorkouts() {
        LocalDate d = LocalDate.of(2026, 7, 1);
        Workout completed = workout(d, 10000, WorkoutStatus.COMPLETED);
        assertThat(matching.findBestMatch(activity(d, 10000), List.of(completed))).isEmpty();
    }
}
