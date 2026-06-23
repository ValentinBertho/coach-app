package com.coachrun;

import com.coachrun.engine.LoadEngine;
import com.coachrun.engine.LoadEngine.LoadMetrics;
import com.coachrun.engine.LoadEngine.SessionLoad;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Vérifie le moteur de charge : ATL/CTL/ratio ACWR, monotonie et répartition par domaine (sRPE).
 */
class LoadEngineTest {

    private final LoadEngine engine = new LoadEngine();
    private final LocalDate ref = LocalDate.of(2026, 6, 23);

    private List<SessionLoad> sessions() {
        return List.of(
                // Dans les 7 derniers jours
                new SessionLoad(ref.minusDays(1), 300, 5),   // domaine 2
                new SessionLoad(ref.minusDays(3), 360, 8),   // domaine 3
                new SessionLoad(ref.minusDays(5), 120, 3),   // domaine 1
                // Entre 8 et 28 jours
                new SessionLoad(ref.minusDays(10), 300, 5),
                new SessionLoad(ref.minusDays(15), 200, 4),
                new SessionLoad(ref.minusDays(20), 350, 6),
                new SessionLoad(ref.minusDays(25), 300, 5));
    }

    @Test
    void computesAcuteChronicAndRatio() {
        LoadMetrics m = engine.compute(sessions(), ref);
        // Charge aiguë = 300+360+120 = 780 ; chronique hebdo = 1930/4 = 482.5 ; ratio ≈ 1.62.
        assertThat(m.acuteLoad7d()).isEqualTo(780.0);
        assertThat(m.chronicLoad28dWeekly()).isEqualTo(482.5);
        assertThat(m.ratio()).isCloseTo(1.62, within(0.03));
        assertThat(m.sessions7d()).isEqualTo(3);
        assertThat(m.sessions28d()).isEqualTo(7);
    }

    @Test
    void computesMonotonyAndDomainDistribution() {
        LoadMetrics m = engine.compute(sessions(), ref);
        assertThat(m.monotony()).isCloseTo(0.77, within(0.05));

        double[] d7 = m.domainPct7d();
        assertThat(d7[0] + d7[1] + d7[2]).isCloseTo(100.0, within(0.2));
        // Domaine 3 (séance RPE 8, 360) dominant sur 7 jours.
        assertThat(d7[2]).isGreaterThan(d7[0]);
        assertThat(d7[2]).isCloseTo(46.2, within(0.5));
    }

    @Test
    void emptyWhenNoSessions() {
        LoadMetrics m = engine.compute(List.of(), ref);
        assertThat(m.acuteLoad7d()).isZero();
        assertThat(m.ratio()).isNull();
        assertThat(m.monotony()).isNull();
        assertThat(m.sessions28d()).isZero();
    }
}
