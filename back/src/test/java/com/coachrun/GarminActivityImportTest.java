package com.coachrun;

import com.coachrun.entity.Activity;
import com.coachrun.entity.ActivityExclusion;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.integration.GarminClient;
import com.coachrun.repository.ActivityExclusionRepository;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.service.DemoSeedService;
import com.coachrun.service.GarminActivityService;
import com.coachrun.service.GarminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sorties poussées par Garmin : une seule sortie par effort, quel que soit l'ordre d'arrivée du
 * résumé, des détails — et de Strava, que la même montre alimente.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@org.springframework.test.context.TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:garmin-activities;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class GarminActivityImportTest {

    /** 2020-01-15 10:00 UTC : loin des sorties de démonstration. */
    private static final long START = 1579082400L;
    private static final LocalDate DAY = LocalDate.of(2020, 1, 15);

    @Autowired private DemoSeedService demoSeedService;
    @Autowired private AthleteRepository athleteRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ActivityExclusionRepository exclusionRepository;
    @Autowired private DeviceConnectionRepository connectionRepository;
    @Autowired private GarminActivityService service;
    @MockBean private GarminClient garminClient;

    private Athlete athlete;

    @BeforeEach
    void setUp() {
        demoSeedService.seed();
        athlete = athleteRepository.findAll().stream()
                .filter(a -> a.getClub() != null).findFirst().orElseThrow();
        connect(List.of(GarminService.WORKOUT_IMPORT, GarminService.ACTIVITY_EXPORT));
    }

    @Test
    void leResumePuisLesDetailsDonnentUneSeuleSortieComplete() {
        service.handle("g-user", List.of(summary(4242L)), false);

        List<Activity> afterSummary = garminActivities();
        assertThat(afterSummary).hasSize(1);
        Activity a = afterSummary.get(0);
        assertThat(a.getActivityDate()).isEqualTo(DAY);
        assertThat(a.getDistanceM()).isEqualTo(12345);
        assertThat(a.getDurationS()).isEqualTo(4321);
        assertThat(a.getRouteJson()).isNull();

        var outcome = service.handle("g-user", List.of(details(4242L)), true);

        assertThat(outcome.completed()).isEqualTo(1);
        List<Activity> afterDetails = garminActivities();
        assertThat(afterDetails).hasSize(1);
        assertThat(afterDetails.get(0).getRouteJson()).isNotNull();
        assertThat(afterDetails.get(0).getStreamJson()).isNotNull();
        assertThat(afterDetails.get(0).getLapsJson()).isNotNull();
    }

    @Test
    void lesDetailsArrivesSeulsSuffisentACreerLaSortie() {
        service.handle("g-user", List.of(details(4243L)), true);
        assertThat(garminActivities()).singleElement()
                .satisfies(a -> assertThat(a.getLapsJson()).isNotNull());
    }

    @Test
    void laMemeNotificationDeuxFoisNeDoublePas() {
        service.handle("g-user", List.of(summary(4244L)), false);
        service.handle("g-user", List.of(summary(4244L)), false);
        assertThat(garminActivities()).hasSize(1);
    }

    @Test
    void uneSortieDejaArriveeParStravaEstCompleteeSansDoublon() {
        Activity strava = new Activity();
        strava.setClub(athlete.getClub());
        strava.setAthlete(athlete);
        strava.setSource(ActivitySource.STRAVA);
        strava.setExternalId("strava-1");
        strava.setActivityDate(DAY);
        strava.setTitle("Sortie longue du coach");
        strava.setDistanceM(12300);
        strava.setDurationS(4300);
        strava = activityRepository.saveAndFlush(strava);

        service.handle("g-user", List.of(details(4245L)), true);

        assertThat(garminActivities()).isEmpty();
        Activity kept = activityRepository.findById(strava.getId()).orElseThrow();
        assertThat(kept.getTitle()).isEqualTo("Sortie longue du coach");
        assertThat(kept.getSource()).isEqualTo(ActivitySource.STRAVA);
        assertThat(kept.getLapsJson()).as("les tours de la montre comblent ce qui manquait").isNotNull();
    }

    @Test
    void sansLaPermissionActivityExportRienNEntre() {
        connect(List.of(GarminService.WORKOUT_IMPORT));
        var outcome = service.handle("g-user", List.of(summary(4246L)), false);
        assertThat(outcome.imported()).isZero();
        assertThat(garminActivities()).isEmpty();
    }

    @Test
    void uneSortieEcarteeParLAthleteNeRevientPas() {
        ActivityExclusion ex = new ActivityExclusion();
        ex.setAthlete(athlete);
        ex.setSource(ActivitySource.GARMIN);
        ex.setExternalId("4247");
        exclusionRepository.saveAndFlush(ex);

        service.handle("g-user", List.of(summary(4247L)), false);

        assertThat(garminActivities()).isEmpty();
    }

    @Test
    void unCompteGarminInconnuEstIgnore() {
        var outcome = service.handle("inconnu", List.of(summary(4248L)), false);
        assertThat(outcome.imported()).isZero();
        assertThat(garminActivities()).isEmpty();
    }

    // --- Outils ---

    private List<Activity> garminActivities() {
        return activityRepository.findByAthleteIdAndActivityDateBetween(athlete.getId(), DAY, DAY).stream()
                .filter(a -> a.getSource() == ActivitySource.GARMIN).toList();
    }

    private void connect(List<String> permissions) {
        DeviceConnection c = connectionRepository
                .findByAthleteIdAndProvider(athlete.getId(), DeviceProvider.GARMIN)
                .orElseGet(DeviceConnection::new);
        c.setAthlete(athlete);
        c.setProvider(DeviceProvider.GARMIN);
        c.setProviderAthleteId("g-user");
        c.setAccessToken("acc");
        c.setRefreshToken("ref");
        c.setExpiresAt(Instant.now().getEpochSecond() + 86400);
        c.setScope(String.join(",", permissions));
        connectionRepository.saveAndFlush(c);
    }

    private static Map<String, Object> summary(long activityId) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", "g-user");
        m.put("summaryId", String.valueOf(activityId));
        m.put("activityId", activityId);
        m.put("activityType", "RUNNING");
        m.put("activityName", "Course à pied");
        m.put("startTimeInSeconds", START);
        m.put("startTimeOffsetInSeconds", 3600);
        m.put("durationInSeconds", 4321);
        m.put("distanceInMeters", 12345.0);
        m.put("averageHeartRateInBeatsPerMinute", 150);
        return m;
    }

    private static Map<String, Object> details(long activityId) {
        Map<String, Object> summary = summary(activityId);
        summary.remove("userId");
        List<Map<String, Object>> samples = new ArrayList<>();
        for (int i = 0; i <= 4320; i += 30) {
            Map<String, Object> s = new HashMap<>();
            s.put("startTimeInSeconds", START + i);
            s.put("latitudeInDegree", 48.85 + i * 1e-5);
            s.put("longitudeInDegree", 2.35);
            s.put("heartRate", 150);
            s.put("speedMetersPerSecond", 2.86);
            s.put("totalDistanceInMeters", i * 2.857);
            s.put("timerDurationInSeconds", i);
            samples.add(s);
        }
        return Map.of("userId", "g-user", "summaryId", activityId + "-detail", "activityId", activityId,
                "summary", summary, "samples", samples,
                "laps", List.of(Map.of("startTimeInSeconds", START), Map.of("startTimeInSeconds", START + 2000)));
    }
}
