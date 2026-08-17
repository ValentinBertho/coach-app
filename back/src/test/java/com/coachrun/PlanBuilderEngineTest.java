package com.coachrun;

import com.coachrun.engine.PlanBuilderEngine;
import com.coachrun.engine.PlanBuilderEngine.Phase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** La trame construite depuis la date de course : phases, affûtage, semaines indisponibles. */
class PlanBuilderEngineTest {

    private final PlanBuilderEngine engine = new PlanBuilderEngine();

    private static final LocalDate START = LocalDate.of(2026, 1, 5);   // un lundi
    private static final LocalDate RACE = LocalDate.of(2026, 4, 12);   // un dimanche, 14 semaines

    @Test
    void weeksRunFromStartToTheRaceWeek() {
        var plan = engine.build(START, RACE, 1.3, 2, 4, List.of());

        assertThat(plan.weeks()).hasSize(14);
        assertThat(plan.weeks().get(0).monday()).isEqualTo(START);
        assertThat(plan.raceMonday()).isEqualTo(LocalDate.of(2026, 4, 6));
        assertThat(plan.weeks().get(13).phase()).isEqualTo(Phase.RACE);
    }

    /** L'affûtage est calé sur la date de course, pas sur le début du plan. */
    @Test
    void taperSitsJustBeforeTheRace() {
        var plan = engine.build(START, RACE, 1.3, 2, 4, List.of());
        var weeks = plan.weeks();

        assertThat(weeks.get(11).phase()).isEqualTo(Phase.TAPER);
        assertThat(weeks.get(12).phase()).isEqualTo(Phase.TAPER);
        // Et il descend : la dernière semaine d'affûtage est plus basse que la première.
        assertThat(weeks.get(12).volumeFactor()).isLessThan(weeks.get(11).volumeFactor());
    }

    @Test
    void deloadWeeksAppearAtTheRequestedRhythm() {
        var plan = engine.build(START, RACE, 1.3, 2, 4, List.of());
        assertThat(plan.weeks().stream().filter(w -> w.phase() == Phase.DELOAD)).isNotEmpty();
        assertThat(plan.weeks().stream().filter(w -> w.phase() == Phase.DELOAD))
                .allMatch(w -> w.volumeFactor() < 1.0);
    }

    @Test
    void volumeClimbsTowardsThePeak() {
        var plan = engine.build(START, RACE, 1.3, 0, 0, List.of());
        double first = plan.weeks().get(0).volumeFactor();
        double last = plan.weeks().get(plan.weeks().size() - 2).volumeFactor();
        assertThat(first).isEqualTo(1.0);
        assertThat(last).isGreaterThan(first);
        assertThat(plan.weeks()).allMatch(w -> w.volumeFactor() <= 1.3);
    }

    /** Une semaine entièrement couverte par une coupure déclarée reste visible, mais vide. */
    @Test
    void fullyUnavailableWeeksAreMarkedSkipped() {
        var holiday = new LocalDate[]{LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 8)};
        var plan = engine.build(START, RACE, 1.3, 2, 4, List.<LocalDate[]>of(holiday));

        var week = plan.weeks().stream()
                .filter(w -> w.monday().equals(LocalDate.of(2026, 2, 2)))
                .findFirst().orElseThrow();
        assertThat(week.skipped()).isTrue();
        assertThat(week.label()).isEqualTo("Indisponible");
        assertThat(plan.summary()).contains("indisponibilité");
    }

    /** Trois jours de coupure ne suppriment pas une semaine : ils déplacent quelques séances. */
    @Test
    void partiallyUnavailableWeeksAreNotSkipped() {
        var shortBreak = new LocalDate[]{LocalDate.of(2026, 2, 3), LocalDate.of(2026, 2, 5)};
        var plan = engine.build(START, RACE, 1.3, 2, 4, List.<LocalDate[]>of(shortBreak));

        assertThat(plan.weeks().stream()
                .filter(w -> w.monday().equals(LocalDate.of(2026, 2, 2)))
                .findFirst().orElseThrow().skipped()).isFalse();
    }

    @Test
    void aRaceInThePastPlansNothing() {
        var plan = engine.build(LocalDate.of(2026, 4, 20), RACE, 1.3, 2, 4, List.of());
        assertThat(plan.weeks()).isEmpty();
        assertThat(plan.summary()).contains("déjà passée");
    }

    @Test
    void aVeryDistantRaceIsCapped() {
        var plan = engine.build(START, START.plusWeeks(60), 1.3, 2, 4, List.of());
        assertThat(plan.weeks()).hasSize(PlanBuilderEngine.MAX_WEEKS);
    }
}
