package com.coachrun;

import com.coachrun.engine.PhysioDetectionEngine;
import com.coachrun.engine.PhysioDetectionEngine.Effort;
import com.coachrun.engine.VdotEngine;
import com.coachrun.entity.enums.RunDistance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * « Ton niveau a bougé ». L'essentiel de ce test porte sur ce que le moteur <b>refuse</b> de
 * proposer : une proposition par sortie transformerait la file du coach en bruit, et c'est ainsi
 * qu'on cesse de lire une file.
 */
class PhysioDetectionEngineTest {

    private final VdotEngine vdot = new VdotEngine();
    private final PhysioDetectionEngine engine = new PhysioDetectionEngine(vdot);

    @Test
    void aFasterTenKilometerRaiseIsProposed() {
        var detection = engine.detect(List.of(new Effort(10_000, 41 * 60 + 20)), 44.0);

        assertThat(detection).isNotNull();
        assertThat(detection.distance()).isEqualTo(RunDistance.D10KM);
        assertThat(detection.gain()).isPositive();
        assertThat(detection.title()).contains("VDOT");
        assertThat(detection.reason()).contains("41:20");
    }

    @Test
    void aFirstEffortBecomesAReferenceRatherThanAGain() {
        var detection = engine.detect(List.of(new Effort(10_000, 41 * 60 + 20)), null);

        assertThat(detection).isNotNull();
        assertThat(detection.gain()).isZero();
        assertThat(detection.title()).contains("Premier repère");
    }

    @Test
    void shortEffortsAreIgnored() {
        assertThat(engine.detect(List.of(new Effort(1500, 5 * 60)), 40.0)).isNull();
    }

    /** Un gain dérisoire n'est pas une décision à prendre : c'est du bruit. */
    @Test
    void marginalGainsAreIgnored() {
        var effort = new Effort(10_000, 41 * 60 + 20);
        double worth = vdot.vdot(effort.distanceM(), effort.durationS());
        // Un demi-point au-dessus du profil : vrai, et sans intérêt pour qui que ce soit.
        assertThat(engine.detect(List.of(effort), worth - 0.5)).isNull();
    }

    /** Un gain énorme trahit une mesure fausse bien plus souvent qu'un progrès. */
    @Test
    void implausibleGainsAreIgnored() {
        assertThat(engine.detect(List.of(new Effort(10_000, 32 * 60)), 35.0)).isNull();
    }

    /** Un profil ne se dégrade pas parce qu'on a couru lentement un dimanche. */
    @Test
    void slowOutingsNeverLowerTheProfile() {
        assertThat(engine.detect(List.of(new Effort(10_000, 55 * 60)), 48.0)).isNull();
    }

    @Test
    void theBestEffortOfTheOutingWins() {
        var slowLongOne = new Effort(10_000, 52 * 60);
        var sharpOne = new Effort(5_000, 20 * 60);
        // Prémisse du test : le 5 km est bien le meilleur des deux.
        assertThat(vdot.vdot(sharpOne.distanceM(), sharpOne.durationS()))
                .isGreaterThan(vdot.vdot(slowLongOne.distanceM(), slowLongOne.durationS()));

        var detection = engine.detect(List.of(slowLongOne, sharpOne),
                vdot.vdot(sharpOne.distanceM(), sharpOne.durationS()) - 2);

        assertThat(detection).isNotNull();
        assertThat(detection.distance()).isEqualTo(RunDistance.D5KM);
    }

    /** Une distance qui ne tombe pas sur une référence n'en devient pas une : le record serait faux. */
    @Test
    void oddDistancesYieldNoReferenceDistance() {
        var odd = new Effort(11_400, 47 * 60);
        var detection = engine.detect(List.of(odd), vdot.vdot(odd.distanceM(), odd.durationS()) - 2);
        assertThat(detection).isNotNull();
        assertThat(detection.distance()).isNull();
    }
}
