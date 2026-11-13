package com.coachrun;

import com.coachrun.FitFileBuilder.Field;
import com.coachrun.dto.request.ActivityImportRequest;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.CorosActivityImport;
import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.ActivitySport;
import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.integration.CorosMcpClient;
import com.coachrun.integration.CorosMcpClient.Endpoints;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.CorosActivityImportRepository;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.service.ActivityService;
import com.coachrun.service.ClockService;
import com.coachrun.service.CorosActivityService;
import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import static com.coachrun.FitFileBuilder.TYPE_U32;
import static com.coachrun.FitFileBuilder.TYPE_U8;
import static com.coachrun.FitFileBuilder.fitTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Import des sorties COROS : chaque nouvelle sortie est téléchargée une fois et une seule, une
 * sortie déjà arrivée par Strava n'est pas téléchargée, et le quota COROS est respecté.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:coros-import;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class CorosActivityImportTest {

    private static final String LIST = "querySportRecords";
    private static final String URLS = "queryActivityFitFileDownloadUrls";
    private static final String FIT_URL = "https://oss.coros.com/fit/476051234567.fit?sig=x";
    private static final Endpoints EU = new Endpoints("https://mcpeu.coros.com",
            "https://mcpeu.coros.com/oauth2/authorize", "https://mcpeu.coros.com/oauth2/token",
            "https://mcpeu.coros.com/connect/register", null, "https://mcpeu.coros.com/mcp");

    private static final List<Field> SESSION = List.of(
            new Field(2, 4, TYPE_U32),     // start_time
            new Field(5, 1, TYPE_U8),      // sport
            new Field(6, 1, TYPE_U8),      // sub_sport
            new Field(8, 4, TYPE_U32),     // total_timer_time (ms)
            new Field(9, 4, TYPE_U32));    // total_distance (cm)

    @Autowired private DemoSeedService demoSeedService;
    @Autowired private AthleteRepository athleteRepository;
    @Autowired private DeviceConnectionRepository connectionRepository;
    @Autowired private CorosActivityImportRepository importRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ActivityService activityService;
    @Autowired private CorosActivityService importService;
    @Autowired private ClockService clock;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CorosMcpClient corosClient;

    private Athlete athlete;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        demoSeedService.seed();
        athlete = athleteRepository.findAll().stream()
                .filter(a -> a.getClub() != null).findFirst().orElseThrow();
        day = clock.today().minusDays(1);
        when(corosClient.isConfigured()).thenReturn(true);
        when(corosClient.endpointsFor(anyString())).thenReturn(EU);
        when(corosClient.endpoints()).thenReturn(EU);
        when(corosClient.callTool(any(), anyString(), eq(URLS), anyMap()))
                .thenReturn(text("1 file: 476051234567.fit " + FIT_URL));
        when(corosClient.downloadFit(eq(FIT_URL), anyString())).thenReturn(fit(day, 12_345, 3_725));
        connect();
    }

    @Test
    void uneNouvelleSortieEstImporteeUneSeuleFois() {
        listing(record("476051234567", 100, day, null, null));

        var result = importService.importAthlete(athlete.getId());

        assertThat(result.imported()).isEqualTo(1);
        var activity = activityRepository.findByAthleteIdAndSourceAndExternalId(
                athlete.getId(), ActivitySource.COROS, "476051234567").orElseThrow();
        assertThat(activity.getActivityDate()).isEqualTo(day);
        assertThat(activity.getDistanceM()).isEqualTo(12_345);
        assertThat(activity.getDurationS()).isEqualTo(3_725);
        assertThat(activity.getSport()).isEqualTo(ActivitySport.RUN);
        assertThat(importService.importedCount(athlete.getId())).isEqualTo(1);
        clearInvocations(corosClient);

        importService.importAthlete(athlete.getId());

        verify(corosClient, never()).callTool(any(), anyString(), eq(URLS), anyMap());
        verify(corosClient, never()).downloadFit(anyString(), anyString());
    }

    @Test
    void uneSortieDejaArriveeParStravaNEstPasTelechargee() {
        activityService.importActivity(athlete.getClub().getId(), athlete.getId(), new ActivityImportRequest(
                ActivitySource.STRAVA, "s-42", day, "Footing du matin", 12_345, 3_725, null, null,
                ActivitySport.RUN, null, false));
        listing(record("476051234567", 100, day, "12.35", "1:02:05"));

        var result = importService.importAthlete(athlete.getId());

        assertThat(result.merged()).isEqualTo(1);
        assertThat(result.imported()).isZero();
        verify(corosClient, never()).downloadFit(anyString(), anyString());
        assertThat(importRepository.findByAthleteIdAndLabelId(athlete.getId(), "476051234567").orElseThrow()
                .getOutcome()).isEqualTo(CorosActivityImport.Outcome.MERGED);
    }

    @Test
    void leQuotaDeTelechargementEstRespecte() {
        for (int i = 0; i < CorosActivityService.DAILY_DOWNLOADS; i++) {
            CorosActivityImport row = new CorosActivityImport();
            row.setAthleteId(athlete.getId());
            row.setLabelId("old-" + i);
            row.setOutcome(CorosActivityImport.Outcome.IMPORTED);
            row.setDownloadedAt(Instant.now().minusSeconds(3600));
            importRepository.save(row);
        }
        listing(record("476051234567", 100, day, null, null));

        var result = importService.importAthlete(athlete.getId());

        assertThat(result.quotaReached()).isTrue();
        verify(corosClient, never()).callTool(any(), anyString(), eq(URLS), anyMap());
    }

    @Test
    void unFichierIllisibleEstRetenteQuelquesFoisPuisAbandonne() {
        when(corosClient.downloadFit(eq(FIT_URL), anyString())).thenReturn("pas un fit".getBytes());
        listing(record("476051234567", 100, day, null, null));

        for (int i = 0; i < 5; i++) {
            importService.importAthlete(athlete.getId());
        }

        verify(corosClient, times(3)).downloadFit(eq(FIT_URL), anyString());
        var row = importRepository.findByAthleteIdAndLabelId(athlete.getId(), "476051234567").orElseThrow();
        assertThat(row.getOutcome()).isEqualTo(CorosActivityImport.Outcome.FAILED);
        assertThat(row.getLastError()).isNotBlank();
    }

    @Test
    void unJetonRefuseNeCasseRien() {
        when(corosClient.callTool(any(), anyString(), eq(LIST), anyMap()))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        var result = importService.importAthlete(athlete.getId());

        assertThat(result.failed()).isEqualTo(1);
        verify(corosClient, never()).downloadFit(anyString(), anyString());
    }

    @Test
    void laDeconnexionOublieLeSuivi() {
        listing(record("476051234567", 100, day, null, null));
        importService.importAthlete(athlete.getId());

        importService.forgetAthlete(athlete.getId());

        assertThat(importRepository.existsByAthleteId(athlete.getId())).isFalse();
        // La sortie, elle, reste.
        assertThat(activityRepository.findByAthleteIdAndSourceAndExternalId(
                athlete.getId(), ActivitySource.COROS, "476051234567")).isPresent();
    }

    // --- Aides ---------------------------------------------------------------------------------

    private void listing(String... records) {
        StringBuilder sb = new StringBuilder("Sport Records (" + records.length + " records)\n\n");
        for (int i = 0; i < records.length; i++) {
            sb.append(i + 1).append(". ").append(records[i]).append('\n');
        }
        when(corosClient.callTool(any(), anyString(), eq(LIST), anyMap())).thenReturn(text(sb.toString()));
    }

    private static String record(String labelId, int sportType, LocalDate date, String km, String duration) {
        return "Run(" + sportType + ") — " + date + "\n"
                + (km != null ? "   Duration: " + duration + " | Distance: " + km + " km\n" : "")
                + "   labelId: " + labelId + " | sportType: " + sportType + "\n";
    }

    private JsonNode text(String t) {
        var r = objectMapper.createObjectNode();
        r.putArray("content").addObject().put("type", "text").put("text", t);
        return r;
    }

    private static byte[] fit(LocalDate date, int distanceM, int durationS) {
        long start = fitTime(date + "T10:00:00Z");
        return new FitFileBuilder()
                .define(0, 18, SESSION)
                .data(0, SESSION, start, 1, 0, durationS * 1000L, distanceM * 100L)
                .build();
    }

    private void connect() {
        DeviceConnection c = connectionRepository
                .findByAthleteIdAndProvider(athlete.getId(), DeviceProvider.COROS)
                .orElseGet(DeviceConnection::new);
        c.setAthlete(athlete);
        c.setProvider(DeviceProvider.COROS);
        c.setProviderAthleteId("c-user");
        c.setAccessToken("acc");
        c.setRefreshToken("ref");
        c.setExpiresAt(Instant.now().getEpochSecond() + 30L * 86400);
        c.setApiBaseUrl(EU.issuer());
        c.setClientId("client-1");
        connectionRepository.saveAndFlush(c);
    }
}
