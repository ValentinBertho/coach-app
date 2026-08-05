package com.coachrun;

import com.coachrun.dto.response.ActivityLapsResponse.Lap;
import com.coachrun.util.ActivityTrack;
import com.coachrun.util.GpxParser;
import com.coachrun.util.SplitCalculator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Décortiquer une séance : les tours de la montre d'abord, des splits kilométriques à défaut.
 *
 * <p>L'écran « Mes activités » ne montrait qu'une moyenne. Or sur un 10 × 400, la moyenne est le
 * seul chiffre qui ne dit rien : ni l'allure des répétitions, ni leur dérive, ni la récupération.
 * Ces tests fixent les deux sources — les tours d'un TCX, et le repli calculé pour tout ce qui
 * n'en a pas — ainsi que la règle qui les sépare.</p>
 */
class ActivityLapsTest {

    /**
     * Flux synthétique : {@code durationS} secondes à allure constante, un point toutes les 10 s.
     * Même format que celui stocké en base — {@code [elapsedS, hr, paceSecPerKm]}.
     */
    private static List<int[]> steadyStream(int durationS, int paceSecPerKm, int hr) {
        List<int[]> stream = new ArrayList<>();
        for (int t = 0; t <= durationS; t += 10) {
            stream.add(new int[] {t, hr, paceSecPerKm});
        }
        return stream;
    }

    @Test
    void decoupeUnFluxEnSplitsKilometriques() {
        // 30 min à 5:00/km = 6 km pile.
        List<Lap> laps = SplitCalculator.perKilometer(steadyStream(1800, 300, 150), 6000);

        assertThat(laps).hasSize(6);
        assertThat(laps.get(0).index()).isEqualTo(1);
        // Un kilomètre est un kilomètre : le passage est interpolé entre deux échantillons, sinon
        // un point toutes les dix secondes suffirait à afficher « 1 033 m en 5:10 ».
        assertThat(laps).allSatisfy(l -> {
            assertThat(l.distanceM()).isEqualTo(1000);
            assertThat(l.durationS()).isEqualTo(300);
            assertThat(l.paceSPerKm()).isEqualTo(300);
            assertThat(l.avgHr()).isEqualTo(150);
        });
    }

    /**
     * L'allure du flux est échantillonnée et bruitée : intégrée telle quelle, elle s'écarte de la
     * distance réelle et fait tomber les kilomètres au mauvais endroit. Le total connu de
     * l'activité fait donc autorité — c'est lui qui recale la découpe.
     */
    @Test
    void recaleLesSplitsSurLaDistanceReelleDeLActivite() {
        // Le flux « croit » courir 6 km (30 min à 5:00) ; la montre en a mesuré 5 km.
        List<Lap> laps = SplitCalculator.perKilometer(steadyStream(1800, 300, 140), 5000);

        assertThat(laps).hasSize(5);
        int total = laps.stream().mapToInt(l -> l.distanceM() == null ? 0 : l.distanceM()).sum();
        assertThat(total).isBetween(4950, 5050);
        // 5 km en 30 min : l'allure affichée suit le recalage, pas l'allure brute du flux.
        assertThat(laps.get(0).paceSPerKm()).isBetween(350, 370);
    }

    /** Le kilomètre entamé compte : une sortie de 10,4 km se lit sur onze lignes, pas dix. */
    @Test
    void gardeLeDernierKilometrePartiel() {
        List<Lap> laps = SplitCalculator.perKilometer(steadyStream(1500, 300, 145), 5400);

        assertThat(laps).hasSize(6);
        assertThat(laps.get(5).distanceM()).isBetween(350, 450);
    }

    @Test
    void neDecoupeRienEnDessousDUnKilometre() {
        assertThat(SplitCalculator.perKilometer(steadyStream(120, 300, 150), 400)).isEmpty();
        assertThat(SplitCalculator.perKilometer(List.of(), 5000)).isEmpty();
        assertThat(SplitCalculator.perKilometer(null, 5000)).isEmpty();
    }

    /** Un flux sans allure exploitable (tapis, capteur muet) ne doit pas inventer de distance. */
    @Test
    void ignoreUnFluxSansAllure() {
        List<int[]> muet = new ArrayList<>();
        for (int t = 0; t <= 1800; t += 10) {
            muet.add(new int[] {t, 150, -1});
        }
        assertThat(SplitCalculator.perKilometer(muet, 6000)).isEmpty();
    }

    // --- Tours d'un fichier TCX ---------------------------------------------

    private static final String TCX_INTERVALLES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase><Activities><Activity Sport="Running">
              <Lap StartTime="2026-07-01T08:00:00Z">
                <TotalTimeSeconds>84.0</TotalTimeSeconds>
                <DistanceMeters>400.0</DistanceMeters>
                <AverageHeartRateBpm><Value>168</Value></AverageHeartRateBpm>
                <MaximumHeartRateBpm><Value>176</Value></MaximumHeartRateBpm>
                <Cadence>92</Cadence>
                <Track>
                  <Trackpoint><Time>2026-07-01T08:00:00Z</Time><DistanceMeters>0</DistanceMeters>
                    <Position><LatitudeDegrees>45.75</LatitudeDegrees><LongitudeDegrees>4.85</LongitudeDegrees></Position>
                    <HeartRateBpm><Value>160</Value></HeartRateBpm></Trackpoint>
                  <Trackpoint><Time>2026-07-01T08:01:24Z</Time><DistanceMeters>400</DistanceMeters>
                    <Position><LatitudeDegrees>45.7536</LatitudeDegrees><LongitudeDegrees>4.85</LongitudeDegrees></Position>
                    <HeartRateBpm><Value>176</Value></HeartRateBpm></Trackpoint>
                </Track>
              </Lap>
              <Lap StartTime="2026-07-01T08:01:24Z">
                <TotalTimeSeconds>120.0</TotalTimeSeconds>
                <DistanceMeters>200.0</DistanceMeters>
                <AverageHeartRateBpm><Value>132</Value></AverageHeartRateBpm>
                <Track>
                  <Trackpoint><Time>2026-07-01T08:01:24Z</Time><DistanceMeters>400</DistanceMeters>
                    <Position><LatitudeDegrees>45.7536</LatitudeDegrees><LongitudeDegrees>4.85</LongitudeDegrees></Position>
                    <HeartRateBpm><Value>150</Value></HeartRateBpm></Trackpoint>
                  <Trackpoint><Time>2026-07-01T08:03:24Z</Time><DistanceMeters>600</DistanceMeters>
                    <Position><LatitudeDegrees>45.7554</LatitudeDegrees><LongitudeDegrees>4.85</LongitudeDegrees></Position>
                    <HeartRateBpm><Value>128</Value></HeartRateBpm></Trackpoint>
                </Track>
              </Lap>
            </Activity></Activities></TrainingCenterDatabase>
            """;

    @Test
    void litLesToursDUnTcxSansConfondreAvecLesPointsDeTrace() {
        ActivityTrack.ParsedActivity parsed =
                GpxParser.parse(TCX_INTERVALLES.getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.laps()).hasSize(2);

        Lap effort = parsed.laps().get(0);
        assertThat(effort.index()).isEqualTo(1);
        // 400 m en 84 s : la distance du TOUR, pas le cumul du dernier point de trace (400 aussi
        // ici, mais 600 sur le second tour — c'est ce piège que le lecteur d'enfants directs évite).
        assertThat(effort.distanceM()).isEqualTo(400);
        assertThat(effort.durationS()).isEqualTo(84);
        assertThat(effort.paceSPerKm()).isEqualTo(210);
        assertThat(effort.avgHr()).isEqualTo(168);
        assertThat(effort.maxHr()).isEqualTo(176);
        assertThat(effort.avgCadence()).isEqualTo(92);

        Lap recup = parsed.laps().get(1);
        assertThat(recup.distanceM()).isEqualTo(200);
        assertThat(recup.durationS()).isEqualTo(120);
        assertThat(recup.maxHr()).isNull();
    }

    /**
     * Une montre qui n'a pas découpé la sortie renvoie un tour unique, égal au total : le
     * présenter comme « tour 1 » ferait croire à un découpage qui n'existe pas. On préfère
     * l'absence, et l'écran retombe sur des splits kilométriques.
     */
    @Test
    void unTourUniqueNEstPasUnDecoupage() {
        String tcx = TCX_INTERVALLES.substring(0, TCX_INTERVALLES.indexOf("<Lap StartTime=\"2026-07-01T08:01:24Z\">"))
                + "</Activity></Activities></TrainingCenterDatabase>";

        assertThat(GpxParser.parse(tcx.getBytes(StandardCharsets.UTF_8)).laps()).isEmpty();
    }
}
