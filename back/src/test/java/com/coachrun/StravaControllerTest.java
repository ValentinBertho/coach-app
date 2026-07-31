package com.coachrun;

import com.coachrun.integration.StravaClient;
import com.coachrun.integration.StravaClient.StravaActivity;
import com.coachrun.integration.StravaClient.StravaAthlete;
import com.coachrun.integration.StravaClient.TokenResponse;
import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sync Strava : connexion OAuth (code → jetons) puis import d'activités, avec déduplication. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
// @MockBean force un contexte dédié : on l'isole sur sa propre base mémoire pour éviter
// que Liquibase ne rejoue les migrations sur la base partagée des autres tests.
@org.springframework.test.context.TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:strava-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class StravaControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.coachrun.security.OAuthStateCodec stateCodec;
    @MockBean private StravaClient stravaClient;

    private MockMvc mvc;
    private String bearer;
    private String clubId;
    private String athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode coach = login(DemoSeedService.HEAD_COACH_EMAIL);
        bearer = "Bearer " + coach.get("accessToken").asText();
        clubId = coach.get("user").get("clubId").asText();
        athleteId = login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText();
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void connectsThenImportsAndDedupes() throws Exception {
        long future = Instant.now().getEpochSecond() + 3600;
        when(stravaClient.isConfigured()).thenReturn(true);
        when(stravaClient.exchangeCode("auth-code")).thenReturn(
                new TokenResponse("acc-1", "ref-1", future, "activity:read", new StravaAthlete(99L)));
        when(stravaClient.listActivities(anyString(), anyLong())).thenReturn(List.of(
                stravaActivity(1001L, "Sortie longue", 12000.0, 3600, "2026-06-20T08:00:00Z"),
                stravaActivity(1002L, "Fractionné", 8000.0, 2400, "2026-06-21T18:00:00Z")));

        // Connexion (state signé requis, comme renvoyé par Strava après l'autorisation)
        String state = stateCodec.issue(java.util.UUID.fromString(athleteId));
        JsonNode connected = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/strava/connect", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"auth-code\",\"state\":\"" + state + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(connected.get("connected").asBoolean()).isTrue();
        assertThat(connected.get("providerAthleteId").asText()).isEqualTo("99");

        // Import : 2 nouvelles activités
        JsonNode imp1 = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/strava/import", clubId, athleteId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(imp1.get("imported").asInt()).isEqualTo(2);

        // Réimport des mêmes activités : déduplication → 0
        JsonNode imp2 = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/strava/import", clubId, athleteId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(imp2.get("imported").asInt()).isZero();
    }

    @Test
    void connectRejectsMissingOrTamperedState() throws Exception {
        when(stravaClient.isConfigured()).thenReturn(true);

        // Sans state → 400 (anti-CSRF)
        mvc.perform(post("/clubs/{c}/athletes/{a}/strava/connect", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"auth-code\"}"))
                .andExpect(status().isBadRequest());

        // State d'un autre athlète (signature valide mais cible différente) → 400
        String foreignState = stateCodec.issue(java.util.UUID.randomUUID());
        mvc.perform(post("/clubs/{c}/athletes/{a}/strava/connect", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"auth-code\",\"state\":\"" + foreignState + "\"}"))
                .andExpect(status().isBadRequest());

        // Signature altérée → 400
        String tampered = stateCodec.issue(java.util.UUID.fromString(athleteId)) + "x";
        mvc.perform(post("/clubs/{c}/athletes/{a}/strava/connect", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"auth-code\",\"state\":\"" + tampered + "\"}"))
                .andExpect(status().isBadRequest());
    }

    /** Quatre points sur les quais de Saône, encodés au format Google (summary_polyline). */
    private static final String POLYLINE = "opfvGogr\\gEgEgEkHkHkH";

    /** Activité Strava complète : capteurs, tracé et flux, comme la vraie API les renvoie. */
    private static StravaActivity stravaActivity(long id, String name, Double distance,
                                                 Integer movingTime, String startDateLocal) {
        return new StravaActivity(id, name, "Run", distance, movingTime, 120.0,
                145.0, 178.0, 3.3, 88.0, 240.0, 620.0, 90.0, "b123", 0, 1,
                new StravaClient.ActivityMap("m" + id, POLYLINE), startDateLocal);
    }

    /**
     * Un athlète Strava a désormais carte et temps en zone, comme un import GPX : le tracé vient
     * de la polyline déjà présente dans la réponse, les flux d'un appel dédié.
     */
    @Test
    void importFillsRouteAndStreamsFromStrava() throws Exception {
        long future = Instant.now().getEpochSecond() + 3600;
        when(stravaClient.isConfigured()).thenReturn(true);
        when(stravaClient.exchangeCode("auth-code")).thenReturn(
                new TokenResponse("acc-1", "ref-1", future, "activity:read_all", new StravaAthlete(99L)));
        when(stravaClient.listActivities(anyString(), anyLong())).thenReturn(List.of(
                stravaActivity(2001L, "Sortie tracée", 10000.0, 3000, "2026-06-22T09:00:00Z")));
        when(stravaClient.activityStreams(anyString(), anyLong())).thenReturn(
                new StravaClient.ActivityStreams(
                        List.of(0.0, 60.0, 120.0), List.of(130.0, 150.0, 162.0), List.of(3.0, 3.3, 4.0)));

        String state = stateCodec.issue(java.util.UUID.fromString(athleteId));
        mvc.perform(post("/clubs/{c}/athletes/{a}/strava/connect", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"auth-code\",\"state\":\"" + state + "\"}"))
                .andExpect(status().isOk());
        JsonNode imp = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/strava/import", clubId, athleteId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(imp.get("imported").asInt()).isEqualTo(1);

        JsonNode activities = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode imported = null;
        for (JsonNode a : activities) {
            if ("2026-06-22".equals(a.get("activityDate").asText())) {
                imported = a;
            }
        }
        assertThat(imported).as("activité importée absente de la liste").isNotNull();
        String activityId = imported.get("id").asText();

        // La carte : le tracé décodé est servi par l'endpoint existant, sans autre modification.
        JsonNode route = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/activities/{id}/route", clubId, athleteId, activityId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(route).hasSize(4);
        assertThat(route.get(0).get(0).asDouble()).isEqualTo(45.75);

        // Le temps en zone : il s'alimente du flux, jusqu'ici rempli par le seul import GPX.
        mvc.perform(get("/clubs/{c}/athletes/{a}/activities/{id}/time-in-zone", clubId, athleteId, activityId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk());
    }

    /** Le quota Strava (100 req / 15 min) ne doit pas casser la synchro : import sans les flux. */
    @Test
    void importSurvivesMissingStreams() throws Exception {
        long future = Instant.now().getEpochSecond() + 3600;
        when(stravaClient.isConfigured()).thenReturn(true);
        when(stravaClient.exchangeCode("auth-code")).thenReturn(
                new TokenResponse("acc-1", "ref-1", future, "activity:read_all", new StravaAthlete(99L)));
        when(stravaClient.listActivities(anyString(), anyLong())).thenReturn(List.of(
                stravaActivity(3001L, "Sortie sans flux", 9000.0, 2700, "2026-06-23T09:00:00Z")));
        when(stravaClient.activityStreams(anyString(), anyLong())).thenReturn(null);

        String state = stateCodec.issue(java.util.UUID.fromString(athleteId));
        mvc.perform(post("/clubs/{c}/athletes/{a}/strava/connect", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"auth-code\",\"state\":\"" + state + "\"}"))
                .andExpect(status().isOk());

        JsonNode imp = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/strava/import", clubId, athleteId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(imp.get("imported").asInt()).isEqualTo(1);
    }

    @Test
    void authorizeFailsWhenNotConfigured() throws Exception {
        when(stravaClient.isConfigured()).thenReturn(false);
        mvc.perform(get("/clubs/{c}/athletes/{a}/strava/authorize", clubId, athleteId)
                        .header("Authorization", bearer))
                .andExpect(status().isServiceUnavailable());
    }
}
