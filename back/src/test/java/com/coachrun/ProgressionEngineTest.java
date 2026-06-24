package com.coachrun;

import com.coachrun.engine.ProgressionEngine;
import com.coachrun.engine.ProgressionEngine.Alert;
import com.coachrun.engine.ProgressionEngine.AlertLevel;
import com.coachrun.engine.ProgressionEngine.DoneSet;
import com.coachrun.engine.ProgressionEngine.Suggestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Progression automatique (§6.7) et alertes coach (§6.8) de la force. */
class ProgressionEngineTest {

    private final ProgressionEngine engine = new ProgressionEngine();

    @Test
    void suggestsLightStepUnder40kg() {
        // Toutes séries OK, RIR 3 > cible 2, douleur 0 → +2,5 kg (charge < 40).
        Suggestion s = engine.suggest(8, 2, List.of(
                new DoneSet(8, 3, 7.0, 0, 30.0),
                new DoneSet(8, 3, 7.0, 0, 30.0)), 30.0);
        assertThat(s.recommended()).isTrue();
        assertThat(s.deltaKg()).isEqualTo(2.5);
    }

    @Test
    void suggestsHeavyStepFrom40kg() {
        Suggestion s = engine.suggest(5, 1, List.of(new DoneSet(5, 2, 8.0, 0, 100.0)), 100.0);
        assertThat(s.recommended()).isTrue();
        assertThat(s.deltaKg()).isEqualTo(5.0);
    }

    @Test
    void noProgressionWhenRirAtTargetOrPain() {
        // RIR égal à la cible (pas strictement supérieur) → maintenir.
        assertThat(engine.suggest(5, 2, List.of(new DoneSet(5, 2, 8.0, 0, 80.0)), 80.0).recommended()).isFalse();
        // Douleur > 2 → maintenir même si reps/RIR OK.
        assertThat(engine.suggest(5, 1, List.of(new DoneSet(5, 3, 7.0, 4, 80.0)), 80.0).recommended()).isFalse();
        // Série non complétée → maintenir.
        assertThat(engine.suggest(8, 1, List.of(new DoneSet(6, 3, 7.0, 0, 80.0)), 80.0).recommended()).isFalse();
    }

    @Test
    void highAlertOnPainInReath() {
        List<Alert> alerts = engine.alerts(null, true,
                List.of(new DoneSet(8, 2, 6.0, 5, 20.0)), null, 20.0);
        assertThat(alerts).anyMatch(a -> a.level() == AlertLevel.HIGH && a.code().equals("PAIN_REATH"));
    }

    @Test
    void mediumAlertsOnRpeAndRirZero() {
        List<Alert> alerts = engine.alerts(2, false,
                List.of(new DoneSet(5, 0, 9.5, 0, 100.0)), null, 100.0);
        assertThat(alerts).anyMatch(a -> a.level() == AlertLevel.MEDIUM && a.code().equals("RPE_HIGH"));
        assertThat(alerts).anyMatch(a -> a.level() == AlertLevel.MEDIUM && a.code().equals("RIR_ZERO"));
    }

    @Test
    void highAlertOnChargeDrop() {
        // 80 kg vs 100 kg précédent = -20 % > 15 % → alerte haute.
        List<Alert> alerts = engine.alerts(2, false,
                List.of(new DoneSet(5, 2, 7.0, 0, 80.0)), 100.0, 80.0);
        assertThat(alerts).anyMatch(a -> a.level() == AlertLevel.HIGH && a.code().equals("CHARGE_DROP"));
    }

    @Test
    void noAlertsOnCleanSession() {
        List<Alert> alerts = engine.alerts(2, false,
                List.of(new DoneSet(5, 3, 7.0, 0, 100.0)), 98.0, 100.0);
        assertThat(alerts).isEmpty();
    }
}
