package com.coachrun;

import com.coachrun.engine.OneRmEngine;
import com.coachrun.engine.OneRmEngine.WorkZone;
import com.coachrun.entity.enums.RmFormula;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Vérifie le moteur 1RM : Nuzzo (validation 15 kg × 6 → 16.764 kg), autres formules,
 * conversion RPE↔RIR, charge cible arrondie 2,5 kg et zones de travail Lacourpaille.
 */
class OneRmEngineTest {

    private final OneRmEngine engine = new OneRmEngine();

    @Test
    void nuzzoMatchesReferenceValue() {
        // Référence CDC : 15 kg × 6 reps → 16.764 kg (constante 104.9).
        assertThat(engine.nuzzo1RM(15, 6)).isCloseTo(16.764, within(0.01));
    }

    @Test
    void otherFormulasAreConsistent() {
        // 100 kg × 5 reps : Epley = 100 × (1 + 5/30) ≈ 116.7 ; Brzycki = 100 × 36/32 = 112.5.
        assertThat(engine.epley1RM(100, 5)).isCloseTo(116.67, within(0.1));
        assertThat(engine.brzycki1RM(100, 5)).isCloseTo(112.5, within(0.1));
        // 1 rep = la charge elle-même.
        assertThat(engine.epley1RM(120, 1)).isEqualTo(120);
        assertThat(engine.brzycki1RM(120, 1)).isEqualTo(120);
    }

    @Test
    void rirBasedUsesEffectiveReps() {
        // 100 kg, 8 reps réalisées + RIR 2 = 10 reps effectives → 73.9 % → 1RM ≈ 135.3.
        assertThat(engine.rirBased1RM(100, 8, 2)).isCloseTo(135.3, within(0.3));
    }

    @Test
    void convertsRpeToRir() {
        assertThat(engine.rirFromRpe(8)).isEqualTo(2);
        assertThat(engine.rirFromRpe(10)).isEqualTo(0);
    }

    @Test
    void chargeForPctRoundsToTwoAndHalf() {
        // 120 kg × 82 % = 98.4 → arrondi au palier 2,5 = 97.5.
        assertThat(engine.chargeForPct(120, 82)).isEqualTo(97.5);
        assertThat(engine.chargeForPct(120, 85)).isEqualTo(102.5);
    }

    @Test
    void derivesLacourpailleZones() {
        List<WorkZone> zones = engine.zones(100);
        assertThat(zones).hasSize(4);
        WorkZone powerSpeed = zones.get(0);
        assertThat(powerSpeed.name()).isEqualTo("Puissance-vitesse");
        assertThat(powerSpeed.kgMin()).isEqualTo(30.0);
        assertThat(powerSpeed.kgMax()).isEqualTo(50.0);
        WorkZone maxForce = zones.get(2);
        assertThat(maxForce.name()).isEqualTo("Force maximale");
        assertThat(maxForce.kgMin()).isEqualTo(70.0);
    }

    @Test
    void estimateDispatchesByFormula() {
        assertThat(engine.estimate(15, 6, null, RmFormula.NUZZO)).isCloseTo(16.764, within(0.01));
        assertThat(engine.estimate(100, 5, null, RmFormula.EPLEY)).isCloseTo(116.67, within(0.1));
    }
}
