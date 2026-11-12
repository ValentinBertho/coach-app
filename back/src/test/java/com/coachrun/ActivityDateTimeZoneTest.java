package com.coachrun;

import com.coachrun.util.ActivityFileParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Une sortie partie à 0 h 30 heure de Paris (23 h 30 UTC la veille) est datée du jour où
 * l'athlète l'a courue, pas de la veille.
 */
class ActivityDateTimeZoneTest {

    private static final String GPX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><name>Nocturne</name><trkseg>
                <trkpt lat="45.7640" lon="4.8357"><time>2026-11-10T23:30:00Z</time></trkpt>
                <trkpt lat="45.7650" lon="4.8367"><time>2026-11-10T23:31:00Z</time></trkpt>
                <trkpt lat="45.7660" lon="4.8377"><time>2026-11-10T23:32:00Z</time></trkpt>
              </trkseg></trk>
            </gpx>
            """;

    @Test
    void nightRunIsDatedInTheApplicationTimeZone() {
        byte[] bytes = GPX.getBytes(StandardCharsets.UTF_8);
        assertThat(ActivityFileParser.parse(bytes, ZoneId.of("Europe/Paris")).date())
                .isEqualTo(LocalDate.of(2026, 11, 11));
        // Comportement précédent, en UTC : la veille.
        assertThat(ActivityFileParser.parse(bytes).date()).isEqualTo(LocalDate.of(2026, 11, 10));
    }
}
