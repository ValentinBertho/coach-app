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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Charge d'entraînement bout-en-bout : une séance datée du jour, renseignée (RPE + durée),
 * produit une charge aiguë et un ratio ACWR cohérents.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AthleteLoadControllerTest {

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
    void computesLoadFromFeedback() throws Exception {
        String today = LocalDate.now().toString();

        // Séance du jour de 60 min pour l'athlète démo.
        String workoutId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scheduledDate\":\"" + today
                                        + "\",\"type\":\"ENDURANCE\",\"title\":\"EF\",\"targetDurationS\":3600}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // Retour athlète : RPE 6 → charge 6 × 60 = 360.
        mvc.perform(patch("/me/workouts/{w}/feedback", workoutId)
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"rpe\":6}"))
                .andExpect(status().isOk());

        JsonNode load = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/load", clubId, athleteId).header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        // Au moins la séance ajoutée (360) ; d'autres séances de démo peuvent s'y ajouter.
        assertThat(load.get("acuteLoad7d").asDouble()).isGreaterThanOrEqualTo(360.0);
        assertThat(load.get("sessions7d").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(load.get("ratio").isNull()).isFalse();
        double d1 = load.get("distribution7d").get("domain1Pct").asDouble();
        double d2 = load.get("distribution7d").get("domain2Pct").asDouble();
        double d3 = load.get("distribution7d").get("domain3Pct").asDouble();
        assertThat(d1 + d2 + d3).isGreaterThan(0);
    }
}
