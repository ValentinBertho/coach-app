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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boucle de progression force : l'athlète renseigne ses séries → recalcul automatique du e1RM,
 * historique alimenté et profil 1RM mis à jour s'il dépasse le courant.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StrengthResultE1rmTest {

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
    void resultsRecomputeE1rmAndUpdateProfile() throws Exception {
        String exerciseId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/pp/exercises", clubId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Squat\",\"category\":\"FORCE_MAX\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        String sessionId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/pp/sessions", clubId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Force max\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(put("/clubs/{c}/pp/sessions/{s}/structure", clubId, sessionId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"structure":{"blocks":[{"id":"b1","blockType":"PRINCIPAL","format":"CLASSIQUE",
                              "exercises":[{"exerciseId":"%s","exerciseName":"Squat","setType":"STANDARD",
                                "prescription":{"chargeRefType":"KG_FIXE","chargeKgMin":100,"sets":3,"repsFixed":5}}]}]}}"""
                                .formatted(exerciseId)))
                .andExpect(status().isOk());
        String scheduledId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/pp/sessions/{s}/schedule", clubId, athleteId, sessionId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"date\":\"2026-06-25\",\"fieldsPreset\":\"AVANCE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // L'athlète renseigne 3 séries : 100 kg × 5 @ RIR 2 → e1RM ≈ 117.5 kg.
        JsonNode updates = objectMapper.readTree(mvc.perform(
                        post("/me/pp/scheduled/{id}/results", scheduledId)
                                .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    [{"exerciseId":"%s","setNumber":1,"chargeKg":100,"repsDone":5,"rirDone":2},
                                     {"exerciseId":"%s","setNumber":2,"chargeKg":100,"repsDone":5,"rirDone":1},
                                     {"exerciseId":"%s","setNumber":3,"chargeKg":100,"repsDone":4,"rirDone":1}]"""
                                        .formatted(exerciseId, exerciseId, exerciseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(updates).hasSize(1);
        double e1rm = updates.get(0).get("e1rmKg").asDouble();
        assertThat(e1rm).isBetween(110.0, 125.0);

        // Le profil 1RM de l'athlète a été créé/mis à jour (source estimated).
        JsonNode profile = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/pp/1rm", clubId, athleteId).header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(profile).hasSize(1);
        assertThat(profile.get(0).get("source").asText()).isEqualTo("estimated");
        assertThat(profile.get(0).get("rmKg").asDouble()).isEqualTo(e1rm);

        // L'historique e1RM contient un point.
        JsonNode history = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/pp/1rm/{e}/history", clubId, athleteId, exerciseId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(history).hasSize(1);
    }
}
