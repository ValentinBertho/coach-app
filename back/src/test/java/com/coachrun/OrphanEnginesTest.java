package com.coachrun;

import com.coachrun.dto.session.CourseBlock;
import com.coachrun.dto.response.CalculatedBlockResponse;
import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.dto.response.CalculatedSessionResponse.CalculatedBlockEntry;
import com.coachrun.engine.CriticalSpeedEngine;
import com.coachrun.engine.CriticalSpeedEngine.Trial;
import com.coachrun.engine.PaceUtil;
import com.coachrun.engine.PlannedLoadEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Les trois moteurs qui n'avaient aucune couverture — les seuls du paquet {@code engine}.
 *
 * <p>Ce n'est pas de la dette anodine : {@link CriticalSpeedEngine} produit la vitesse critique,
 * qui ancre ensuite toutes les cibles de séance, et {@link PlannedLoadEngine} alimente la charge
 * prévue affichée au coach dans son calendrier hebdomadaire.</p>
 */
class OrphanEnginesTest {

    // --- Vitesse critique ------------------------------------------------------

    private final CriticalSpeedEngine cs = new CriticalSpeedEngine();

    /**
     * Modèle à deux paramètres : d = CS·t + D'. Avec 1000 m en 200 s et 3000 m en 660 s, la pente
     * vaut (3000−1000)/(660−200) ≈ 4,35 m/s et l'ordonnée à l'origine ≈ 130 m.
     */
    @Test
    void derivesCriticalSpeedAndAnaerobicReserveFromTwoMaximalEfforts() {
        var r = cs.compute(List.of(new Trial(1000, 200), new Trial(3000, 660)));

        assertThat(r.vcMs()).isCloseTo(4.348, within(0.01));
        assertThat(r.dPrimeM()).isCloseTo(130.4, within(1.0));
    }

    /** Trois efforts alignés doivent redonner exactement la même droite. */
    @Test
    void staysConsistentWithAThirdAlignedEffort() {
        var two = cs.compute(List.of(new Trial(1000, 200), new Trial(3000, 660)));
        var three = cs.compute(List.of(
                new Trial(1000, 200), new Trial(2000, 430), new Trial(3000, 660)));

        assertThat(three.vcMs()).isCloseTo(two.vcMs(), within(0.05));
    }

    @Test
    void refusesASingleEffort() {
        assertThatThrownBy(() -> cs.compute(List.of(new Trial(1000, 200))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deux efforts");
    }

    @Test
    void refusesEffortsSharingTheSameDuration() {
        assertThatThrownBy(() -> cs.compute(List.of(new Trial(1000, 200), new Trial(1200, 200))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distincts");
    }

    /** Un athlète plus rapide sur le long que sur le court donne une pente absurde : on refuse. */
    @Test
    void refusesIncoherentEfforts() {
        assertThatThrownBy(() -> cs.compute(List.of(new Trial(3000, 200), new Trial(1000, 660))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Charge prévue ---------------------------------------------------------

    private final PlannedLoadEngine planned = new PlannedLoadEngine();

    private CalculatedBlockEntry block(Integer rpe, Integer estimatedDurationS) {
        CourseBlock b = new CourseBlock(
                null, null, null, null, null, null, null, rpe, null, null, null, null);
        CalculatedBlockResponse calc = new CalculatedBlockResponse(
                true, null, null, null, null, null, null, null, null, null, null, null, null,
                estimatedDurationS, null, false);
        return new CalculatedBlockEntry(b, calc, null);
    }

    /** sRPE : RPE × minutes. 40 min à RPE 6 puis 20 min à RPE 3 = 240 + 60 = 300 UA. */
    @Test
    void sumsBlockLoadsInArbitraryUnits() {
        var session = new CalculatedSessionResponse(
                List.of(block(3, 1200)), List.of(block(6, 2400)), List.of(), null, null);

        assertThat(planned.compute(session)).isEqualTo(300);
    }

    /** Un bloc sans RPE prescrit ne contribue pas : mieux vaut sous-estimer qu'inventer. */
    @Test
    void ignoresBlocksWithoutAPrescribedRpe() {
        var session = new CalculatedSessionResponse(
                List.of(block(null, 3600)), List.of(block(6, 600)), List.of(), null, null);

        assertThat(planned.compute(session)).isEqualTo(60);
    }

    @Test
    void returnsNullWhenNothingIsExploitable() {
        assertThat(planned.compute(null)).isNull();
        assertThat(planned.compute(new CalculatedSessionResponse(
                List.of(), List.of(), List.of(), null, null))).isNull();
    }

    // --- Conversions d'allure --------------------------------------------------

    /** 3,5 m/s ≈ 4:46 /km ≈ 12,6 km/h — les trois représentations doivent concorder. */
    @Test
    void paceSpeedConversionsAreConsistent() {
        double secPerKm = PaceUtil.msToSecPerKm(3.5);
        assertThat(secPerKm).isCloseTo(285.7, within(0.5));
        assertThat(PaceUtil.secPerKmToMs(secPerKm)).isCloseTo(3.5, within(0.01));
        assertThat(PaceUtil.secPerKmToKmh(secPerKm)).isCloseTo(12.6, within(0.1));
    }

    @Test
    void formatsPaceAsMinutesAndSeconds() {
        assertThat(PaceUtil.formatPace(285)).isEqualTo("4:45");
        assertThat(PaceUtil.formatPace(300)).isEqualTo("5:00");
        // Les secondes sous 10 doivent rester sur deux chiffres : « 4:5 » n'est pas une allure.
        assertThat(PaceUtil.formatPace(245)).isEqualTo("4:05");
    }
}
