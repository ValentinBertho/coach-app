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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tableau de bord coach « état de forme » : répartition Route/Trail et pastille de forme
 * dérivée du dernier retour (fatigue + douleur).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachFormDashboardTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String coachBearer;
    private String athleteBearer;
    private String clubId;
    private String athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        JsonNode coach = login(DemoSeedService.HEAD_COACH_EMAIL);
        coachBearer = "Bearer " + coach.get("accessToken").asText();
        clubId = coach.get("user").get("clubId").asText();

        JsonNode athlete = login(DemoSeedService.ATHLETE_EMAIL);
        athleteBearer = "Bearer " + athlete.get("accessToken").asText();
        athleteId = athlete.get("user").get("athleteId").asText();
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void formDashboardSplitsByDisciplineAndShowsFormPill() throws Exception {
        // Athlète en Trail.
        mvc.perform(put("/clubs/{c}/athletes/{a}/physio", clubId, athleteId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discipline\":\"TRAIL\"}"))
                .andExpect(status().isOk());

        // Séance + retour fatigue élevée (8) → état de forme rouge.
        String workoutId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scheduledDate\":\"2026-06-20\",\"type\":\"INTERVALS\",\"title\":\"Test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        mvc.perform(patch("/me/workouts/{w}/feedback", workoutId)
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"fatigue\":8,\"pain\":0}"))
                .andExpect(status().isOk());

        JsonNode dash = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/dashboard/form", clubId).header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(dash.get("total").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(dash.get("trail").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode mine = null;
        for (JsonNode row : dash.get("trailAthletes")) {
            if (athleteId.equals(row.get("id").asText())) {
                mine = row;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("formStatus").asText()).isEqualTo("RED");
        assertThat(mine.get("fatigue").asInt()).isEqualTo(8);
        assertThat(mine.get("lastFeedbackDate").asText()).isEqualTo("2026-06-20");
    }
}
