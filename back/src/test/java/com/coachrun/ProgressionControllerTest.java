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

/** Progression auto + alertes coach : flux assignation → retour athlète → suggestion coach. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProgressionControllerTest {

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
    void recommendsProgressionWhenAllSetsBeatTarget() throws Exception {
        // Exercice + 1RM
        String exerciseId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/pp/exercises", clubId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Squat prog\",\"category\":\"FORCE_MAX\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(put("/clubs/{c}/athletes/{a}/pp/1rm", clubId, athleteId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"" + exerciseId + "\",\"rmKg\":100}"))
                .andExpect(status().isOk());

        // Séance + structure (cible : 5 reps, RIR 1)
        String sessionId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/pp/sessions", clubId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Force\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(put("/clubs/{c}/pp/sessions/{s}/structure", clubId, sessionId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                            {"structure":{"blocks":[{"id":"b1","blockType":"PRINCIPAL","format":"CLASSIQUE","exercises":[
                              {"exerciseId":"%s","exerciseName":"Squat prog","setType":"STANDARD",
                               "prescription":{"chargeRefType":"PCT_RM_RANGE","chargePctRmMin":80,"chargePctRmMax":85,
                                               "effortRefType":"RIR_RANGE","rirMin":1,"rirMax":3,"sets":3,"repsFixed":5}}]}]}}
                            """).formatted(exerciseId)))
                .andExpect(status().isOk());

        // Assignation au calendrier de l'athlète
        String today = java.time.LocalDate.now().toString();
        String scheduledId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/pp/sessions/{s}/schedule", clubId, athleteId, sessionId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"date\":\"" + today + "\",\"fieldsPreset\":\"AVANCE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // Retour athlète : 3 séries à 85 kg, 5 reps, RIR 3 (> cible 1), douleur 0.
        String results = ("""
            [{"exerciseId":"%s","setNumber":1,"chargeKg":85,"repsDone":5,"rpeDone":7,"rirDone":3,"pain":0},
             {"exerciseId":"%s","setNumber":2,"chargeKg":85,"repsDone":5,"rpeDone":7,"rirDone":3,"pain":0},
             {"exerciseId":"%s","setNumber":3,"chargeKg":85,"repsDone":5,"rpeDone":8,"rirDone":3,"pain":0}]
            """).formatted(exerciseId, exerciseId, exerciseId);
        mvc.perform(post("/me/pp/scheduled/{s}/results", scheduledId)
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content(results))
                .andExpect(status().isOk());

        // Suggestion coach : progression recommandée +5 kg (charge ≥ 40).
        JsonNode prog = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/pp/scheduled/{s}/progression", clubId, athleteId, scheduledId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode ex = prog.get("exercises").get(0);
        assertThat(ex.get("recommended").asBoolean()).isTrue();
        assertThat(ex.get("deltaKg").asDouble()).isEqualTo(5.0);
        // Séance propre : aucune alerte.
        assertThat(prog.get("alerts")).isEmpty();
    }
}
