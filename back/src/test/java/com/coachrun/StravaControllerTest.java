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
                new StravaActivity(1001L, "Sortie longue", "Run", 12000.0, 3600, 120.0, 145.0, "2026-06-20T08:00:00Z"),
                new StravaActivity(1002L, "Fractionné", "Run", 8000.0, 2400, 60.0, 160.0, "2026-06-21T18:00:00Z")));

        // Connexion
        JsonNode connected = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/strava/connect", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"auth-code\"}"))
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
    void authorizeFailsWhenNotConfigured() throws Exception {
        when(stravaClient.isConfigured()).thenReturn(false);
        mvc.perform(get("/clubs/{c}/athletes/{a}/strava/authorize", clubId, athleteId)
                        .header("Authorization", bearer))
                .andExpect(status().isServiceUnavailable());
    }
}
