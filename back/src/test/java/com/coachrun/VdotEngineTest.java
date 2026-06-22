package com.coachrun;

import com.coachrun.engine.VdotEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie le moteur VDOT (Daniels) : ancrage sur une valeur connue, monotonie et cohérence
 * de l'inversion (allure ↔ VDOT).
 */
class VdotEngineTest {

    private final VdotEngine engine = new VdotEngine();

    @Test
    void vdotOfKnownPerformanceMatchesDanielsTable() {
        // 5 km en 19:57 (1197 s) ≈ VDOT 50 (table Jack Daniels).
        assertThat(engine.vdot(5000, 1197)).isCloseTo(50.0, org.assertj.core.api.Assertions.within(1.0));
    }

    @Test
    void vdotDecreasesWithSlowerTime() {
        double faster = engine.vdot(5000, 1100);
        double slower = engine.vdot(5000, 1300);
        assertThat(faster).isGreaterThan(slower);
    }

    @Test
    void racePaceInversionRoundTrips() {
        double targetVdot = 50.0;
        int pace5kmSecPerKm = engine.racePaceSecPerKm(targetVdot, 5000);
        int total5kmSeconds = pace5kmSecPerKm * 5;
        // Reconstruire le VDOT à partir de l'allure calculée → on retombe sur la cible.
        assertThat(engine.vdot(5000, total5kmSeconds))
                .isCloseTo(targetVdot, org.assertj.core.api.Assertions.within(0.4));
    }

    @Test
    void shorterDistancesHaveFasterEquivalencePace() {
        int pace800 = engine.racePaceSecPerKm(50.0, 800);
        int paceMarathon = engine.racePaceSecPerKm(50.0, 42195);
        // Allure plus rapide ⇒ moins de secondes par km.
        assertThat(pace800).isLessThan(paceMarathon);
    }
}
