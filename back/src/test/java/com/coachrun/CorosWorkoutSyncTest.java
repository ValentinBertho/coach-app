package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.CorosWorkoutSync;
import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.integration.CorosMcpClient;
import com.coachrun.integration.CorosMcpClient.Endpoints;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.CorosWorkoutSyncRepository;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.service.ClockService;
import com.coachrun.service.CorosWorkoutSyncService;
import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Envoi des séances course vers le calendrier COROS : créées une fois, réécrites quand le coach
 * les change, et, faute de suppression chez COROS, réécrites « Annulée par ton coach » quand
 * elles sont retirées ou déplacées.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:coros-sync;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class CorosWorkoutSyncTest {

    private static final String CREATE = "createScheduledWorkout";
    private static final String UPDATE = "updateScheduledWorkout";
    private static final Endpoints EU = new Endpoints("https://mcpeu.coros.com",
            "https://mcpeu.coros.com/oauth2/authorize", "https://mcpeu.coros.com/oauth2/token",
            "https://mcpeu.coros.com/connect/register", null, "https://mcpeu.coros.com/mcp");

    @Autowired private DemoSeedService demoSeedService;
    @Autowired private AthleteRepository athleteRepository;
    @Autowired private WorkoutRepository workoutRepository;
    @Autowired private DeviceConnectionRepository connectionRepository;
    @Autowired private CorosWorkoutSyncRepository syncRepository;
    @Autowired private CorosWorkoutSyncService syncService;
    @Autowired private ClockService clock;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CorosMcpClient corosClient;

    private Athlete athlete;
    private LocalDate today;
    private final AtomicLong ids = new AtomicLong(5000);

    @BeforeEach
    void setUp() {
        demoSeedService.seed();
        athlete = athleteRepository.findAll().stream()
                .filter(a -> a.getClub() != null).findFirst().orElseThrow();
        today = clock.today();
        when(corosClient.isConfigured()).thenReturn(true);
        when(corosClient.endpointsFor(anyString())).thenReturn(EU);
        when(corosClient.endpoints()).thenReturn(EU);
        when(corosClient.callTool(any(), anyString(), anyString(), anyMap()))
                .thenAnswer(inv -> answer(ids.incrementAndGet()));
        connect();
        // Le suivi des séances de démo ne compte pas : on part de ce qui est déjà envoyé.
        syncService.syncAthlete(athlete.getId());
        clearInvocations(corosClient);
    }

    @Test
    void uneSeanceCourseAVenirEstCreeeASaDate() {
        Workout w = workout("Seuil 3 × 10 min", WorkoutType.THRESHOLD, today.plusDays(2));

        syncService.syncAthlete(athlete.getId());

        verify(corosClient).callTool(eq(EU), eq("acc"), eq(CREATE), argThat(
                a -> course("Seuil 3 × 10 min").matches(a)
                        && today.plusDays(2).format(DateTimeFormatter.BASIC_ISO_DATE).equals(a.get("date"))));
        CorosWorkoutSync row = rowFor(w);
        assertThat(row.getIdInPlan()).isNotBlank();
        assertThat(row.getLastError()).isNull();
        assertThat(syncService.summary(athlete.getId()).workoutsOnCoros()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void uneSeanceInchangeeNeCouteAucunAppel() {
        workout("Footing", WorkoutType.ENDURANCE, today.plusDays(1));
        syncService.syncAthlete(athlete.getId());
        clearInvocations(corosClient);

        syncService.syncAthlete(athlete.getId());

        verify(corosClient, never()).callTool(any(), anyString(), anyString(), anyMap());
    }

    @Test
    void uneSeanceModifieeEstReecriteSurPlace() {
        Workout w = workout("Footing", WorkoutType.ENDURANCE, today.plusDays(1));
        syncService.syncAthlete(athlete.getId());
        String first = rowFor(w).getIdInPlan();
        clearInvocations(corosClient);

        w.setTitle("Footing vallonné");
        workoutRepository.saveAndFlush(w);
        syncService.syncAthlete(athlete.getId());

        verify(corosClient).callTool(eq(EU), eq("acc"), eq(UPDATE), argThat(
                a -> first.equals(a.get("idInPlan")) && course("Footing vallonné").matches(a)));
        verify(corosClient, never()).callTool(any(), anyString(), eq(CREATE), anyMap());
        assertThat(rowFor(w).getIdInPlan()).isNotEqualTo(first); // COROS en renvoie un nouveau
    }

    @Test
    void uneSeanceRetireeEstReecriteAnnulee() {
        Workout w = workout("Sortie longue", WorkoutType.LONG_RUN, today.plusDays(3));
        syncService.syncAthlete(athlete.getId());
        String id = rowFor(w).getIdInPlan();
        clearInvocations(corosClient);

        workoutRepository.delete(w);
        workoutRepository.flush();
        syncService.syncAthlete(athlete.getId());

        verify(corosClient).callTool(eq(EU), eq("acc"), eq(UPDATE), argThat(
                a -> id.equals(a.get("idInPlan")) && course("Annulée par ton coach").matches(a)));
        CorosWorkoutSync row = syncRepository.findByAthleteId(athlete.getId()).stream()
                .filter(r -> r.isCancelled() && "Sortie longue".equals(r.getTitle()))
                .findFirst().orElseThrow();
        assertThat(row.getWorkoutId()).isNull();
    }

    @Test
    void uneSeanceDeplaceeEstAnnuleeEtRecreeAuNouveauJour() {
        Workout w = workout("Footing", WorkoutType.ENDURANCE, today.plusDays(1));
        syncService.syncAthlete(athlete.getId());
        clearInvocations(corosClient);

        w.setScheduledDate(today.plusDays(4));
        workoutRepository.saveAndFlush(w);
        syncService.syncAthlete(athlete.getId());

        verify(corosClient).callTool(any(), anyString(), eq(UPDATE), argThat(course("Annulée par ton coach")));
        verify(corosClient).callTool(any(), anyString(), eq(CREATE), argThat(
                a -> course("Footing").matches(a)
                        && today.plusDays(4).format(DateTimeFormatter.BASIC_ISO_DATE).equals(a.get("date"))));
    }

    @Test
    void uneSeanceAnnuleeResertQuandUneAutreArriveLeMemeJour() {
        Workout w = workout("Footing", WorkoutType.ENDURANCE, today.plusDays(5));
        syncService.syncAthlete(athlete.getId());
        workoutRepository.delete(w);
        workoutRepository.flush();
        syncService.syncAthlete(athlete.getId());
        String cancelledId = syncRepository.findByAthleteId(athlete.getId()).stream()
                .filter(r -> r.isCancelled() && today.plusDays(5).equals(r.getScheduledDate()))
                .findFirst().orElseThrow().getIdInPlan();
        clearInvocations(corosClient);

        Workout other = workout("Fartlek", WorkoutType.INTERVALS, today.plusDays(5));
        syncService.syncAthlete(athlete.getId());

        verify(corosClient).callTool(any(), anyString(), eq(UPDATE), argThat(
                a -> cancelledId.equals(a.get("idInPlan")) && course("Fartlek").matches(a)));
        verify(corosClient, never()).callTool(any(), anyString(), eq(CREATE), anyMap());
        assertThat(rowFor(other).isCancelled()).isFalse();
    }

    @Test
    void siCorosRefuseLaFormeStructureeLaFormeSimpleEstEnvoyee() {
        doAnswer(inv -> {
            Map<String, Object> args = inv.getArgument(3);
            @SuppressWarnings("unchecked")
            var course = (Map<String, Object>) args.get("course");
            if (((List<?>) course.get("sections")).size() > 1) {
                throw new CorosMcpClient.McpException("invalid section");
            }
            return answer(ids.incrementAndGet());
        }).when(corosClient).callTool(any(), anyString(), eq(CREATE), anyMap());
        Workout w = workout("Seuil", WorkoutType.THRESHOLD, today.plusDays(2));
        w.setTargetDistanceM(null);
        w.replaceSteps(List.of(step(com.coachrun.entity.enums.WorkoutStepType.WARMUP, 2000, w),
                step(com.coachrun.entity.enums.WorkoutStepType.REPETITION, 5000, w)));
        workoutRepository.saveAndFlush(w);

        syncService.syncAthlete(athlete.getId());

        verify(corosClient, times(2)).callTool(any(), anyString(), eq(CREATE), argThat(course("Seuil")));
        assertThat(rowFor(w).getIdInPlan()).isNotBlank();
        assertThat(rowFor(w).getLastError()).isNull();
    }

    @Test
    void unJetonRefuseArreteLEnvoiEtLeSignale() {
        doThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))
                .when(corosClient).callTool(any(), anyString(), anyString(), anyMap());
        Workout w = workout("Footing", WorkoutType.ENDURANCE, today.plusDays(1));
        workout("Footing 2", WorkoutType.ENDURANCE, today.plusDays(2));

        var result = syncService.syncAthlete(athlete.getId());

        assertThat(result.failed()).isEqualTo(1);
        verify(corosClient, times(1)).callTool(any(), anyString(), anyString(), anyMap());
        assertThat(rowFor(w).getLastError()).isNotBlank();
    }

    @Test
    void leRenforcementNEstPasEnvoye() {
        workout("Gainage", WorkoutType.STRENGTH, today.plusDays(1));

        syncService.syncAthlete(athlete.getId());

        verify(corosClient, never()).callTool(any(), anyString(), anyString(), anyMap());
    }

    @Test
    void laDeconnexionOublieLeSuivi() {
        workout("Footing", WorkoutType.ENDURANCE, today.plusDays(1));
        syncService.syncAthlete(athlete.getId());

        syncService.forgetAthlete(athlete.getId());

        assertThat(syncRepository.findByAthleteId(athlete.getId())).isEmpty();
    }

    @Test
    void lIdInPlanEstLuDansLeTexteOuLeContenuStructure() throws Exception {
        assertThat(idInPlanOf("{\"content\":[{\"type\":\"text\",\"text\":\"Created. idInPlan: 42\"}]}")).isEqualTo("42");
        assertThat(idInPlanOf("{\"structuredContent\":{\"data\":{\"idInPlan\":\"77\"}},\"content\":[]}")).isEqualTo("77");
        assertThat(idInPlanOf("{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"idInPlan\\\":9}\"}]}")).isEqualTo("9");
    }

    // --- Aides ---------------------------------------------------------------------------------

    private String idInPlanOf(String json) throws Exception {
        var m = CorosWorkoutSyncService.class.getDeclaredMethod("idInPlanOf", JsonNode.class);
        m.setAccessible(true);
        return (String) m.invoke(null, objectMapper.readTree(json));
    }

    private JsonNode answer(long id) {
        return objectMapper.createObjectNode().set("content", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("type", "text").put("text", "OK, idInPlan: " + id)));
    }

    @SuppressWarnings("unchecked")
    private static ArgumentMatcher<Map<String, Object>> course(String name) {
        return a -> a != null && a.get("course") instanceof Map<?, ?> c && name.equals(((Map<String, Object>) c).get("courseName"));
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

    private Workout workout(String title, WorkoutType type, LocalDate date) {
        Workout w = new Workout();
        w.setClub(athlete.getClub());
        w.setAthlete(athlete);
        w.setStatus(WorkoutStatus.PLANNED);
        w.setScheduledDate(date);
        w.setType(type);
        w.setTitle(title);
        w.setTargetDistanceM(8000);
        return workoutRepository.saveAndFlush(w);
    }

    private static com.coachrun.entity.WorkoutStep step(com.coachrun.entity.enums.WorkoutStepType type, int distanceM,
                                                       Workout w) {
        var s = new com.coachrun.entity.WorkoutStep();
        s.setWorkout(w);
        s.setStepType(type);
        s.setDistanceM(distanceM);
        s.setRepetitions(1);
        return s;
    }

    private CorosWorkoutSync rowFor(Workout w) {
        UUID id = w.getId();
        return syncRepository.findByAthleteId(athlete.getId()).stream()
                .filter(r -> id.equals(r.getWorkoutId()))
                .findFirst().orElseThrow();
    }
}
