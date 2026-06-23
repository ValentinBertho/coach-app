package com.coachrun;

import com.coachrun.engine.LactateThresholdEngine;
import com.coachrun.engine.LactateThresholdEngine.LTDetectionResult;
import com.coachrun.engine.LactateThresholdEngine.StepPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Vérifie la détection des seuils lactiques : LT1 (repos + 0,5 mmol/L) et LT2 (Dmax modifié).
 */
class LactateThresholdEngineTest {

    private final LactateThresholdEngine engine = new LactateThresholdEngine();

    /** Profil de test : lactate croissant, FC croissante. */
    private List<StepPoint> steps() {
        return List.of(
                new StepPoint(3.0, 1.0, 130),
                new StepPoint(3.3, 1.2, 140),
                new StepPoint(3.6, 1.8, 150),
                new StepPoint(3.9, 3.0, 160),
                new StepPoint(4.2, 5.5, 170),
                new StepPoint(4.5, 8.0, 178));
    }

    @Test
    void detectsLt1AtBaselinePlusHalf() {
        LTDetectionResult r = engine.detect(steps(), 0.8);
        // baseline 0.8 → seuil LT1 = 1.3 mmol/L ; croisement entre 3.3 (1.2) et 3.6 (1.8) ≈ 3.35 m/s.
        assertThat(r.baseline()).isEqualTo(0.8);
        assertThat(r.lt1Threshold()).isEqualTo(1.3);
        assertThat(r.lt1Ms()).isCloseTo(3.35, within(0.05));
        assertThat(r.fcLt1()).isBetween(140, 144);
    }

    @Test
    void detectsLt2WithModifiedDmax() {
        LTDetectionResult r = engine.detect(steps(), 0.8);
        // Distance perpendiculaire max à la droite (LT1 → dernier palier) atteinte à 3.9 m/s.
        assertThat(r.lt2Ms()).isEqualTo(3.9);
        assertThat(r.fcLt2()).isEqualTo(160);
        assertThat(r.lt2Ms()).isGreaterThan(r.lt1Ms());
    }

    @Test
    void usesFirstStepAsBaselineWhenRestNotProvided() {
        LTDetectionResult r = engine.detect(steps(), null);
        assertThat(r.baseline()).isEqualTo(1.0);          // premier palier
        assertThat(r.lt1Threshold()).isEqualTo(1.5);
    }

    @Test
    void returnsEmptyWhenInsufficientSteps() {
        LTDetectionResult r = engine.detect(List.of(new StepPoint(3.0, 1.0, 130)), 0.8);
        assertThat(r.lt1Ms()).isNull();
        assertThat(r.lt2Ms()).isNull();
    }
}
