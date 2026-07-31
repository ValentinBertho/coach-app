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

    /** Athlète installé : 28 jours d'historique et 8 séances → ACWR publiable. */
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
                new SessionLoad(ref.minusDays(25), 300, 5),
                new SessionLoad(ref.minusDays(27), 200, 4));
    }

    @Test
    void computesAcuteChronicAndRatio() {
        LoadMetrics m = engine.compute(sessions(), ref);
        // Charge aiguë = 300+360+120 = 780 ; chronique hebdo = 2130/4 = 532.5 ; ratio ≈ 1.46.
        assertThat(m.acuteLoad7d()).isEqualTo(780.0);
        assertThat(m.chronicLoad28dWeekly()).isEqualTo(532.5);
        assertThat(m.ratio()).isCloseTo(1.46, within(0.03));
        assertThat(m.ratioReady()).isTrue();
        assertThat(m.sessions7d()).isEqualTo(3);
        assertThat(m.sessions28d()).isEqualTo(8);
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
        assertThat(m.ratioReady()).isFalse();
        assertThat(m.historyDays()).isZero();
    }

    // --- Garde-fou ACWR : pas de ratio tant que l'historique est trop court -----------------

    @Test
    void noRatioForANewAthleteEvenWithManySessions() {
        // 10 séances tassées sur 10 jours : la charge chronique est divisée par 4 semaines dont
        // l'athlète n'en a vécu qu'une et demie → l'ancien calcul sortait un ratio ≈ 4,0 et le
        // cockpit affichait une alerte rouge « risque de blessure » sur un athlète tout neuf.
        LocalDate firstSession = ref.minusDays(10);
        List<SessionLoad> recent = new java.util.ArrayList<>();
        for (int d = 0; d <= 10; d += 1) {
            recent.add(new SessionLoad(ref.minusDays(d), 300, 5));
        }

        LoadMetrics m = engine.compute(recent, ref, firstSession);

        assertThat(m.ratio()).isNull();
        assertThat(m.ratioReady()).isFalse();
        // La progression reste exposée pour l'affichage « ACWR en construction — 10/28 jours ».
        assertThat(m.historyDays()).isEqualTo(10);
        // Les charges brutes, elles, restent calculées et affichables.
        assertThat(m.acuteLoad7d()).isPositive();
    }

    @Test
    void noRatioWhenHistoryIsLongButSessionsAreTooFew() {
        // 4 séances sur 28 jours : la fenêtre est pleine mais l'échantillon ne l'est pas.
        List<SessionLoad> sparse = List.of(
                new SessionLoad(ref.minusDays(2), 300, 5),
                new SessionLoad(ref.minusDays(9), 300, 5),
                new SessionLoad(ref.minusDays(18), 300, 5),
                new SessionLoad(ref.minusDays(26), 300, 5));

        LoadMetrics m = engine.compute(sparse, ref, ref.minusDays(200));

        assertThat(m.ratio()).isNull();
        assertThat(m.ratioReady()).isFalse();
        // L'historique est plafonné à la fenêtre chronique : la barre affiche 28/28.
        assertThat(m.historyDays()).isEqualTo(LoadEngine.CHRONIC_WINDOW_DAYS);
    }

    @Test
    void ratioAppearsExactlyAtTheThresholds() {
        // 21 jours d'historique et 8 séances : les deux seuils sont atteints.
        List<SessionLoad> sessions = new java.util.ArrayList<>();
        for (int d = 0; d < 8; d += 3) {
            sessions.add(new SessionLoad(ref.minusDays(d), 300, 5));
        }
        for (int d = 9; d <= 21; d += 3) {
            sessions.add(new SessionLoad(ref.minusDays(d), 300, 5));
        }
        assertThat(sessions).hasSize(LoadEngine.RATIO_MIN_SESSIONS_28D);

        LoadMetrics m = engine.compute(sessions, ref, ref.minusDays(LoadEngine.RATIO_MIN_HISTORY_DAYS));

        assertThat(m.ratioReady()).isTrue();
        assertThat(m.ratio()).isNotNull();
    }

    @Test
    void aLongBreakDoesNotMakeAnEstablishedAthleteLookNew() {
        // L'athlète court depuis un an mais n'a que 12 jours de séances dans la fenêtre : sans la
        // date réelle de première séance, on le prendrait pour un débutant.
        List<SessionLoad> sessions = new java.util.ArrayList<>();
        for (int d = 0; d <= 12; d += 1) {
            sessions.add(new SessionLoad(ref.minusDays(d), 300, 5));
        }

        assertThat(engine.compute(sessions, ref).ratioReady()).isFalse();
        assertThat(engine.compute(sessions, ref, ref.minusYears(1)).ratioReady()).isTrue();
    }
}
