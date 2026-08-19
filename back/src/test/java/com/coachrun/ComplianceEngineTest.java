package com.coachrun;

import com.coachrun.engine.ComplianceEngine;
import com.coachrun.engine.ComplianceEngine.PrescribedEffort;
import com.coachrun.engine.ComplianceEngine.RealizedLap;
import com.coachrun.engine.ComplianceEngine.Verdict;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * « Séance tenue ? ». Vérifie surtout les deux pièges de l'appariement : les récupérations, qui ne
 * doivent jamais passer pour des répétitions, et l'absence de données, qui ne doit jamais se lire
 * comme un échec.
 */
class ComplianceEngineTest {

    private final ComplianceEngine engine = new ComplianceEngine();

    @Test
    void allRepetitionsInRangeScoresFull() {
        var result = engine.evaluate(prescribed(4, 1000, 200, 210), laps(
                lap(1, 1000, 205), lap(2, 1000, 203), lap(3, 1000, 208), lap(4, 1000, 201)));

        assertThat(result.scorePct()).isEqualTo(100);
        assertThat(result.assessed()).isEqualTo(4);
        assertThat(result.verdictLabel()).isEqualTo("Séance tenue à 100 %");
        assertThat(result.detail()).isEqualTo("Toutes les répétitions dans la fourchette.");
    }

    /** Le vrai sujet : une récupération intercalée ne doit pas décaler toute la séance. */
    @Test
    void recoveryLapsAreSkippedRatherThanScored() {
        List<RealizedLap> withRecoveries = List.of(
                lap(1, 1000, 205), lap(2, 200, 400),
                lap(3, 1000, 203), lap(4, 200, 410),
                lap(5, 1000, 207));

        var result = engine.evaluate(prescribed(3, 1000, 200, 210), withRecoveries);

        assertThat(result.assessed()).isEqualTo(3);
        assertThat(result.scorePct()).isEqualTo(100);
    }

    @Test
    void driftAtTheEndIsNamedAsSuch() {
        var result = engine.evaluate(prescribed(4, 1000, 200, 210), laps(
                lap(1, 1000, 205), lap(2, 1000, 204), lap(3, 1000, 224), lap(4, 1000, 228)));

        assertThat(result.scorePct()).isEqualTo(50);
        assertThat(result.detail()).contains("dérivé").contains("fin de séance");
    }

    @Test
    void goingOutTooFastIsNamedAsSuch() {
        var result = engine.evaluate(prescribed(4, 1000, 200, 210), laps(
                lap(1, 1000, 185), lap(2, 1000, 188), lap(3, 1000, 205), lap(4, 1000, 207)));

        assertThat(result.detail()).contains("Départ plus rapide");
        assertThat(result.efforts().get(0).verdict()).isEqualTo(Verdict.TOO_FAST);
        assertThat(result.efforts().get(0).deltaSecPerKm()).isEqualTo(-15);
    }

    /** Une fourchette absente ne produit pas un échec : elle produit un silence. */
    @Test
    void missingRangeIsNotAFailure() {
        var result = engine.evaluate(
                List.of(new PrescribedEffort("1 km", null, null, 1000, null)),
                laps(lap(1, 1000, 300)));

        assertThat(result.scorePct()).isNull();
        assertThat(result.assessed()).isZero();
        assertThat(result.efforts().get(0).verdict()).isEqualTo(Verdict.NOT_ASSESSED);
    }

    @Test
    void noLapsAtAllYieldsAnEmptyVerdict() {
        assertThat(engine.evaluate(prescribed(4, 1000, 200, 210), List.of()).scorePct()).isNull();
        assertThat(engine.evaluate(List.of(), laps(lap(1, 1000, 300))).scorePct()).isNull();
    }

    /** Cinq secondes au kilomètre : la marge d'un GPS, pas celle d'un athlète. */
    @Test
    void toleranceAbsorbsMeasurementNoise() {
        var result = engine.evaluate(prescribed(1, 1000, 200, 210), laps(lap(1, 1000, 214)));
        assertThat(result.efforts().get(0).verdict()).isEqualTo(Verdict.ON_TARGET);

        var beyond = engine.evaluate(prescribed(1, 1000, 200, 210), laps(lap(1, 1000, 220)));
        assertThat(beyond.efforts().get(0).verdict()).isEqualTo(Verdict.TOO_SLOW);
    }

    // --- Fixtures -------------------------------------------------------------

    // --- FC par bloc et dérive cardiaque -----------------------------------------------------

    /**
     * Ce que le coach lit à voix haute devant une séance : « même chrono, dix pulsations de plus ».
     * La dérive est <b>subie</b> — l'allure n'a pas bougé, le cœur est monté.
     */
    @Test
    void reportsCardiacDriftBetweenFirstAndLastEffort() {
        var result = engine.evaluate(prescribed(4, 1000, 200, 210), List.of(
                lapHr(1, 1000, 205, 150), lapHr(2, 1000, 205, 156),
                lapHr(3, 1000, 205, 160), lapHr(4, 1000, 205, 162)));

        assertThat(result.efforts()).extracting("actualAvgHrBpm")
                .containsExactly(150, 156, 160, 162);

        var drift = result.drift();
        assertThat(drift).isNotNull();
        assertThat(drift.hrDeltaBpm()).isEqualTo(12);
        assertThat(drift.hrDeltaPct()).isEqualTo(8.0);
        assertThat(drift.paceDeltaSecPerKm()).isZero();
        assertThat(drift.firstLabel()).isEqualTo("1000 m (1/4)");
        assertThat(drift.lastLabel()).isEqualTo("1000 m (4/4)");
    }

    /** Dérive <b>compensée</b> : le cœur tient parce que l'allure a lâché. Les deux se distinguent. */
    @Test
    void paceDeltaTellsCompensatedDriftFromEnduredDrift() {
        var result = engine.evaluate(prescribed(3, 1000, 200, 215), List.of(
                lapHr(1, 1000, 202, 158), lapHr(2, 1000, 208, 159), lapHr(3, 1000, 213, 158)));

        var drift = result.drift();
        assertThat(drift.hrDeltaBpm()).isZero();
        assertThat(drift.paceDeltaSecPerKm())
                .as("onze secondes au kilomètre plus lent à la fin")
                .isEqualTo(11);
    }

    /**
     * Un tour sans capteur n'est pas un tour à zéro pulsation : on compare le premier et le
     * dernier effort <i>mesurés</i>, ce qui reste juste quand la ceinture lâche au milieu.
     */
    @Test
    void ignoresEffortsWithoutHeartRate() {
        var result = engine.evaluate(prescribed(4, 1000, 200, 210), List.of(
                lapHr(1, 1000, 205, 150), lapHr(2, 1000, 205, null),
                lapHr(3, 1000, 205, null), lapHr(4, 1000, 205, 165)));

        assertThat(result.drift().hrDeltaBpm()).isEqualTo(15);
    }

    /**
     * Un seul effort mesuré ne donne aucune dérive — et surtout pas « 0 % », qui se lirait comme
     * une séance parfaitement stable alors qu'il n'y a rien à comparer.
     */
    @Test
    void noDriftWhenNothingToCompare() {
        var oneMeasured = engine.evaluate(prescribed(2, 1000, 200, 210), List.of(
                lapHr(1, 1000, 205, 150), lapHr(2, 1000, 205, null)));
        assertThat(oneMeasured.drift()).isNull();

        var noneMeasured = engine.evaluate(prescribed(2, 1000, 200, 210), laps(
                lap(1, 1000, 205), lap(2, 1000, 205)));
        assertThat(noneMeasured.drift()).isNull();
    }

    /** Le pourcentage est arrondi au dixième : la ceinture ne mesure pas au millième. */
    @Test
    void driftPercentageIsRoundedToOneDecimal() {
        var result = engine.evaluate(prescribed(2, 1000, 200, 210), List.of(
                lapHr(1, 1000, 205, 149), lapHr(2, 1000, 205, 156)));

        assertThat(result.drift().hrDeltaPct()).isEqualTo(4.7); // 7 / 149 = 4,6979…
    }

    private List<PrescribedEffort> prescribed(int reps, int distanceM, int fast, int slow) {
        List<PrescribedEffort> out = new ArrayList<>();
        for (int i = 1; i <= reps; i++) {
            out.add(new PrescribedEffort(distanceM + " m (" + i + "/" + reps + ")",
                    fast, slow, distanceM, null));
        }
        return out;
    }

    private List<RealizedLap> laps(RealizedLap... laps) {
        return List.of(laps);
    }

    private RealizedLap lap(int index, int distanceM, int paceSecPerKm) {
        int durationS = (int) Math.round(distanceM / 1000.0 * paceSecPerKm);
        return new RealizedLap(index, distanceM, durationS, paceSecPerKm);
    }

    /** Le même tour, porteur d'une FC moyenne. */
    private RealizedLap lapHr(int index, int distanceM, int paceSecPerKm, Integer avgHrBpm) {
        int durationS = (int) Math.round(distanceM / 1000.0 * paceSecPerKm);
        return new RealizedLap(index, distanceM, durationS, paceSecPerKm, avgHrBpm);
    }
}
