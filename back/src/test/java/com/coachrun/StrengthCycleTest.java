package com.coachrun;

import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cycles de force : création (structure par semaine) et assignation au calendrier d'un athlète. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StrengthCycleTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

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
    void createsCycleAndAssignsToAthlete() throws Exception {
        String sessionId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/pp/sessions", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Séance force\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // Cycle 2 semaines, la séance programmée chaque semaine.
        String cycleId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/pp/cycles", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Force max 2 sem","weeks":2,"objective":"Force",
                             "structure":{"weeks":[
                               {"week":1,"sessionIds":["%s"],"chargePctAdjustment":0},
                               {"week":2,"sessionIds":["%s"],"chargePctAdjustment":5}]}}"""
                                .formatted(sessionId, sessionId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        JsonNode list = objectMapper.readTree(mvc.perform(get("/clubs/{c}/pp/cycles", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(list).isNotEmpty();

        // Assignation à partir d'aujourd'hui.
        String today = java.time.LocalDate.now().toString();
        JsonNode assigned = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/pp/cycles/{id}/assign/{a}", clubId, cycleId, athleteId)
                                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"startDate\":\"" + today + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(assigned.get("scheduled").asInt()).isEqualTo(2);

        // Les 2 séances apparaissent sur 2 semaines.
        String to = java.time.LocalDate.now().plusDays(14).toString();
        JsonNode scheduled = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/pp/scheduled?from=" + today + "&to=" + to, clubId, athleteId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        // Au moins les 2 séances du cycle (le jeu de démo peut en contenir d'autres).
        assertThat(scheduled.size()).isGreaterThanOrEqualTo(2);
    }
}
