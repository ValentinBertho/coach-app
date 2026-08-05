package com.coachrun;

import com.coachrun.util.ActivityTrack;
import com.coachrun.util.GpxParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GpxParserTest {

    @Test
    void parsesGpxTrack() {
        String gpx = """
                <?xml version="1.0"?>
                <gpx><trk><trkseg>
                  <trkpt lat="45.7640" lon="4.8357"><ele>170</ele><time>2026-07-01T08:00:00Z</time></trkpt>
                  <trkpt lat="45.7700" lon="4.8400"><ele>180</ele><time>2026-07-01T08:05:00Z</time></trkpt>
                  <trkpt lat="45.7750" lon="4.8450"><ele>175</ele><time>2026-07-01T08:10:00Z</time></trkpt>
                </trkseg></trk></gpx>
                """;
        ActivityTrack.ParsedActivity a = GpxParser.parse(gpx.getBytes(StandardCharsets.UTF_8));
        assertThat(a.distanceM()).isGreaterThan(500);
        assertThat(a.durationS()).isEqualTo(600);
        assertThat(a.elevationGainM()).isEqualTo(10);
        assertThat(a.route()).hasSize(3);
        assertThat(a.date().toString()).isEqualTo("2026-07-01");
    }

    @Test
    void extractsHeartRateAndStream() {
        String gpx = """
                <?xml version="1.0"?>
                <gpx><trk><trkseg>
                  <trkpt lat="45.7600" lon="4.8357"><time>2026-07-01T08:00:00Z</time>
                    <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>142</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions></trkpt>
                  <trkpt lat="45.7610" lon="4.8357"><time>2026-07-01T08:00:30Z</time>
                    <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>150</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions></trkpt>
                  <trkpt lat="45.7620" lon="4.8357"><time>2026-07-01T08:01:00Z</time>
                    <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>158</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions></trkpt>
                </trkseg></trk></gpx>
                """;
        ActivityTrack.ParsedActivity a = GpxParser.parse(gpx.getBytes(StandardCharsets.UTF_8));
        assertThat(a.stream()).isNotEmpty();
        // Chaque échantillon = [elapsedS, hr, paceSecPerKm] ; la FC est extraite des extensions.
        int[] first = a.stream().get(0);
        assertThat(first[0]).isEqualTo(0);
        assertThat(first[1]).isEqualTo(142);
        assertThat(first[2]).isGreaterThan(0); // allure calculée depuis position + temps
    }
}
