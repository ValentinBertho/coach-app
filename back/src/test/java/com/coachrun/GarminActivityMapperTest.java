package com.coachrun;

import com.coachrun.entity.enums.ActivitySport;
import com.coachrun.integration.GarminActivityMapper;
import com.coachrun.integration.GarminActivityMapper.Parsed;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Notifications Garmin (Activity API) → sortie Darilab, sur des corps écrits d'après la doc. */
class GarminActivityMapperTest {

    @Test
    void unResumeDonneLaSortieEtSaDateLocale() {
        // 2026-11-12 23:30 UTC, montre à UTC+1 : c'est une sortie du 13 pour l'athlète.
        Map<String, Object> body = new HashMap<>();
        body.put("userId", "u");
        body.put("summaryId", "777");
        body.put("activityId", 777L);
        body.put("activityType", "RUNNING");
        body.put("activityName", "Paris Course");
        body.put("startTimeInSeconds", 1794526200L);
        body.put("startTimeOffsetInSeconds", 3600);
        body.put("durationInSeconds", 3000);
        body.put("distanceInMeters", 10012.4);
        body.put("averageHeartRateInBeatsPerMinute", 151);
        body.put("maxHeartRateInBeatsPerMinute", 172.0);
        body.put("averageRunCadenceInStepsPerMinute", 84.0);
        body.put("activeKilocalories", 650);
        body.put("totalElevationGainInMeters", 42.6);
        Parsed p = GarminActivityMapper.fromSummary(body);

        assertThat(p.externalId()).isEqualTo("777");
        assertThat(p.date()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(p.sport()).isEqualTo(ActivitySport.RUN);
        assertThat(p.distanceM()).isEqualTo(10012);
        assertThat(p.durationS()).isEqualTo(3000);
        assertThat(p.avgHr()).isEqualTo(151);
        assertThat(p.maxHr()).isEqualTo(172);
        assertThat(p.avgCadence()).as("cadence Garmin par jambe, doublée").isEqualTo(168);
        assertThat(p.elevationGainM()).isEqualTo(43);
        assertThat(p.hasDetails()).isFalse();
    }

    @Test
    void lesDetailsPortentLeTraceLeFluxEtLesTours() {
        long t0 = 1794500000L;
        List<Map<String, Object>> samples = new ArrayList<>();
        for (int i = 0; i <= 600; i += 10) {
            Map<String, Object> s = new HashMap<>();
            s.put("startTimeInSeconds", t0 + i);
            s.put("latitudeInDegree", 48.85 + i * 1e-5);
            s.put("longitudeInDegree", 2.35);
            s.put("heartRate", i < 300 ? 140 : 170);
            s.put("speedMetersPerSecond", i < 300 ? 3.0 : 4.0);
            s.put("totalDistanceInMeters", i < 300 ? i * 3.0 : 900 + (i - 300) * 4.0);
            s.put("timerDurationInSeconds", i);
            s.put("elevationInMeters", 30.0);
            samples.add(s);
        }
        Parsed p = GarminActivityMapper.fromDetails(Map.of(
                "userId", "u", "summaryId", "888-detail", "activityId", 888L,
                "summary", Map.of("activityType", "TRAIL_RUNNING", "startTimeInSeconds", t0,
                        "startTimeOffsetInSeconds", 0, "durationInSeconds", 600, "distanceInMeters", 2100.0),
                "samples", samples,
                "laps", List.of(Map.of("startTimeInSeconds", t0), Map.of("startTimeInSeconds", t0 + 300))));

        assertThat(p.externalId()).isEqualTo("888");
        assertThat(p.sport()).isEqualTo(ActivitySport.RUN);
        assertThat(p.route()).isNotEmpty();
        assertThat(p.stream()).isNotEmpty();
        assertThat(p.stream().get(0)[1]).isEqualTo(140);
        assertThat(p.stream().get(0)[2]).as("3 m/s ≈ 5'33/km").isEqualTo(333);
        assertThat(p.laps()).hasSize(2);
        assertThat(p.laps().get(0).avgHr()).isEqualTo(140);
        assertThat(p.laps().get(1).avgHr()).isEqualTo(170);
        assertThat(p.laps().get(0).durationS() + p.laps().get(1).durationS()).isEqualTo(600);
        assertThat(p.laps().get(0).distanceM() + p.laps().get(1).distanceM()).isEqualTo(2100);
    }

    @Test
    void sansActivityIdLeSummaryIdSansSuffixeEnTientLieu() {
        Parsed p = GarminActivityMapper.fromDetails(Map.of(
                "summaryId", "999-detail",
                "summary", Map.of("activityType", "CYCLING", "startTimeInSeconds", 1794500000L)));
        assertThat(p.externalId()).isEqualTo("999");
        assertThat(p.sport()).isEqualTo(ActivitySport.RIDE);
        assertThat(p.laps()).isEmpty();
    }

    @Test
    void uneNotificationIncompleteNeDonneRien() {
        assertThat(GarminActivityMapper.fromSummary(Map.of("activityType", "RUNNING"))).isNull();
        assertThat(GarminActivityMapper.fromSummary(Map.of("activityId", 1L))).isNull();
    }

    @Test
    void lesTypesGarminTombentDansLaBonneFamille() {
        assertThat(ActivitySport.fromGarmin("TREADMILL_RUNNING")).isEqualTo(ActivitySport.RUN);
        assertThat(ActivitySport.fromGarmin("LAP_SWIMMING")).isEqualTo(ActivitySport.SWIM);
        assertThat(ActivitySport.fromGarmin("STRENGTH_TRAINING")).isEqualTo(ActivitySport.STRENGTH);
        assertThat(ActivitySport.fromGarmin("HIIT")).isEqualTo(ActivitySport.STRENGTH);
        assertThat(ActivitySport.fromGarmin("HIKING")).isEqualTo(ActivitySport.WALK);
        assertThat(ActivitySport.fromGarmin("INDOOR_CARDIO")).isEqualTo(ActivitySport.OTHER);
        assertThat(ActivitySport.fromGarmin("MULTI_SPORT")).isNull();
        assertThat(ActivitySport.fromGarmin(null)).isNull();
    }

    @Test
    void uneActiviteParenteEstSignalee() {
        Parsed p = GarminActivityMapper.fromSummary(Map.of(
                "activityId", 5L, "activityType", "MULTI_SPORT", "startTimeInSeconds", 1794500000L,
                "isParent", true));
        assertThat(p.parent()).isTrue();
    }
}
