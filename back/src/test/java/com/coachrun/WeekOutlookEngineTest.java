package com.coachrun;

import com.coachrun.engine.WeekOutlookEngine;
import com.coachrun.engine.WeekOutlookEngine.Level;
import com.coachrun.engine.WeekOutlookEngine.PlannedSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** La semaine jugée avant d'être courue : écart à l'habituel et entassement des séances dures. */
class WeekOutlookEngineTest {

    private final WeekOutlookEngine engine = new WeekOutlookEngine();
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 17);

    @Test
    void emptyWeekSaysNothing() {
        assertThat(engine.compute(List.of(), 300, true).sentence()).isNull();
    }

    @Test
    void normalWeekIsAnnouncedWithItsDelta() {
        var outlook = engine.compute(List.of(
                session(0, 100, 4), session(2, 120, 7), session(5, 90, 4)), 300, true);

        assertThat(outlook.plannedLoadUa()).isEqualTo(310);
        assertThat(outlook.deltaPct()).isEqualTo(3);
        assertThat(outlook.level()).isEqualTo(Level.NORMAL);
        assertThat(outlook.sentence()).contains("310 UA").contains("+3 %");
    }

    @Test
    void aBigJumpIsFlaggedWithTheProjectedRatio() {
        var outlook = engine.compute(List.of(
                session(0, 200, 8), session(2, 200, 8), session(4, 150, 4)), 300, true);

        assertThat(outlook.level()).isEqualTo(Level.HIGH);
        assertThat(outlook.projectedAcwr()).isGreaterThan(1.5);
        assertThat(outlook.sentence()).contains("ACWR projeté");
    }

    /** Deux séances dures collées : le problème n'est pas le total, c'est l'espacement. */
    @Test
    void crowdedHardSessionsAreCalledOut() {
        var outlook = engine.compute(List.of(
                session(1, 110, 8), session(2, 110, 9)), 300, true);

        assertThat(outlook.level()).isEqualTo(Level.WATCH);
        assertThat(outlook.sentence()).contains("séances dures rapprochées");
    }

    @Test
    void aDeloadWeekIsNamed() {
        var outlook = engine.compute(List.of(session(0, 80, 3), session(3, 70, 4)), 300, true);
        assertThat(outlook.level()).isEqualTo(Level.CALM);
        assertThat(outlook.sentence()).contains("décharge");
    }

    /**
     * Sans charge chronique fiable, on énonce le fait sans le juger : un « +340 % » sur un athlète
     * qui vient de commencer serait faux, et le premier chiffre faux discrédite tous les suivants.
     */
    @Test
    void noComparisonWithoutEnoughHistory() {
        var outlook = engine.compute(List.of(session(0, 200, 8)), 40, false);

        assertThat(outlook.deltaPct()).isNull();
        assertThat(outlook.projectedAcwr()).isNull();
        assertThat(outlook.sentence()).contains("200 UA prévues").doesNotContain("%");
    }

    private PlannedSession session(int dayOffset, int loadUa, int rpe) {
        return new PlannedSession(MONDAY.plusDays(dayOffset), loadUa, rpe);
    }
}
