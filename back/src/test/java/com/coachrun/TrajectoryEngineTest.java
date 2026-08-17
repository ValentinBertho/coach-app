package com.coachrun;

import com.coachrun.engine.TrajectoryEngine;
import com.coachrun.engine.TrajectoryEngine.Outlook;
import com.coachrun.engine.VdotEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** La trajectoire vers l'objectif : l'écart, et le pronostic tiré de la pente de l'athlète. */
class TrajectoryEngineTest {

    private final VdotEngine vdotEngine = new VdotEngine();
    private final TrajectoryEngine engine = new TrajectoryEngine(vdotEngine);

    private static final int MARATHON_M = 42195;
    private static final int TARGET_3H15 = 3 * 3600 + 15 * 60;

    @Test
    void nothingToSayWithoutAVdot() {
        var t = engine.compute(null, MARATHON_M, TARGET_3H15, 10, 0.2);
        assertThat(t.outlook()).isEqualTo(Outlook.UNKNOWN);
        assertThat(t.headline()).contains("Pas encore de projection");
    }

    @Test
    void projectionWithoutTargetStillSaysSomething() {
        var t = engine.compute(45.0, MARATHON_M, null, 10, null);
        assertThat(t.projectedTimeS()).isPositive();
        assertThat(t.detail()).contains("Aucun chrono visé");
    }

    @Test
    void aheadWhenTheProjectionBeatsTheTarget() {
        // VDOT 55 place le marathon largement sous 3 h 15.
        var t = engine.compute(55.0, MARATHON_M, TARGET_3H15, 8, 0.1);
        assertThat(t.outlook()).isEqualTo(Outlook.AHEAD);
        assertThat(t.gapSeconds()).isNegative();
    }

    @Test
    void onTrackWhenTheObservedSlopeClosesTheGap() {
        double current = 44.0;
        double required = vdotEngine.vdot(MARATHON_M, TARGET_3H15);
        double gap = required - current;
        // Une pente qui comble exactement l'écart sur les semaines restantes.
        var t = engine.compute(current, MARATHON_M, TARGET_3H15, 10, gap / 10);

        assertThat(t.outlook()).isEqualTo(Outlook.ON_TRACK);
        assertThat(t.vdotGap()).isGreaterThan(0);
        assertThat(t.headline()).contains("il manque");
    }

    @Test
    void outOfReachWhenTheSlopeIsFarTooSlow() {
        var t = engine.compute(40.0, MARATHON_M, TARGET_3H15, 4, 0.01);
        assertThat(t.outlook()).isEqualTo(Outlook.OUT_OF_REACH);
    }

    /** Sans pente observée, on ne prétend pas savoir : « à risque », jamais « atteignable ». */
    @Test
    void noSlopeMeansNoPromise() {
        var t = engine.compute(44.0, MARATHON_M, TARGET_3H15, 10, null);
        assertThat(t.outlook()).isEqualTo(Outlook.AT_RISK);
        assertThat(t.detail()).contains("points de VDOT");
    }

    /** Une pente calculée sur moins de quatre semaines est du bruit, pas une tendance. */
    @Test
    void slopeNeedsAtLeastFourWeeks() {
        assertThat(engine.weeklyProgress(44.0, 47.0, 10)).isNull();
        assertThat(engine.weeklyProgress(44.0, 47.0, 56)).isNotNull();
    }

    /** Une régression n'est pas une pente de progression : on ne l'extrapole pas. */
    @Test
    void regressionIsNotASlope() {
        assertThat(engine.weeklyProgress(47.0, 44.0, 56)).isNull();
    }

    @Test
    void durationsReadInRoundUnits() {
        assertThat(TrajectoryEngine.formatDuration(45)).isEqualTo("45 s");
        assertThat(TrajectoryEngine.formatDuration(540)).isEqualTo("9 min");
        assertThat(TrajectoryEngine.formatTime(3 * 3600 + 24 * 60)).isEqualTo("3 h 24");
        assertThat(TrajectoryEngine.formatTime(41 * 60 + 20)).isEqualTo("41:20");
    }
}
