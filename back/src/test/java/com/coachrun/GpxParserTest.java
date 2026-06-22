package com.coachrun;

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
        GpxParser.ParsedActivity a = GpxParser.parse(gpx.getBytes(StandardCharsets.UTF_8));
        assertThat(a.distanceM()).isGreaterThan(500);
        assertThat(a.durationS()).isEqualTo(600);
        assertThat(a.elevationGainM()).isEqualTo(10);
        assertThat(a.route()).hasSize(3);
        assertThat(a.date().toString()).isEqualTo("2026-07-01");
    }
}
