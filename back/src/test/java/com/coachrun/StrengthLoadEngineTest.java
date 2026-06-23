package com.coachrun;

import com.coachrun.engine.StrengthLoadEngine;
import com.coachrun.engine.StrengthLoadEngine.SetLoad;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Charge interne force (UA) : mécanique pondérée %1RM, métabolique sRPE, RPE/durée séance. */
class StrengthLoadEngineTest {

    private final StrengthLoadEngine engine = new StrengthLoadEngine();

    @Test
    void mechanicalLoadWeightsTonnageByIntensity() {
        // 100 kg × 5 reps à 1RM 125 → %1RM = 80 % → 500 × 0.8 = 400 UA.
        double load = engine.mechanicalLoad(List.of(new SetLoad(100, 5, 125.0, 8, 60)));
        assertThat(load).isEqualTo(400.0);
    }

    @Test
    void mechanicalLoadFallsBackToRawTonnageWithoutOneRm() {
        // Sans 1RM connu → tonnage brut 60 × 10 = 600 UA.
        double load = engine.mechanicalLoad(List.of(new SetLoad(60, 10, null, 7, 45)));
        assertThat(load).isEqualTo(600.0);
    }

    @Test
    void mechanicalLoadIgnoresEmptySets() {
        double load = engine.mechanicalLoad(List.of(
                new SetLoad(0, 5, 100.0, 8, 30),
                new SetLoad(50, 0, 100.0, 8, 30)));
        assertThat(load).isZero();
    }

    @Test
    void metabolicLoadIsSessionRpeTimesDuration() {
        // sRPE Foster : RPE 8 × 30 min = 240 UA.
        assertThat(engine.metabolicLoad(8, 30)).isEqualTo(240.0);
        assertThat(engine.metabolicLoad(null, 30)).isZero();
    }

    @Test
    void sessionRpeIsRoundedAverageOfSetRpe() {
        Integer rpe = engine.sessionRpe(List.of(
                new SetLoad(100, 5, 125.0, 7, 60),
                new SetLoad(100, 5, 125.0, 8, 60),
                new SetLoad(100, 5, 125.0, 9, 60)));
        assertThat(rpe).isEqualTo(8);
    }

    @Test
    void totalDurationSumsSetDurationsInMinutes() {
        double min = engine.totalDurationMin(List.of(
                new SetLoad(100, 5, 125.0, 8, 90),
                new SetLoad(100, 5, 125.0, 8, 90)));
        assertThat(min).isEqualTo(3.0);
    }
}
