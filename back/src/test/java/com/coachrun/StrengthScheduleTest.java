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
 * Calendrier de force : assignation (snapshot + charges figées + champs adaptatifs),
 * déplacement par l'athlète et retour de séance (fatigue/douleur).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StrengthScheduleTest {

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
    void scheduleStrengthSnapshotsMoveAndFeedback() throws Exception {
        // Exercice + 1RM + séance + structure.
        String exerciseId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/pp/exercises", clubId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Squat\",\"category\":\"FORCE_MAX\",\"muscleGroups\":[\"QUADRICEPS\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(put("/clubs/{c}/athletes/{a}/pp/1rm", clubId, athleteId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"" + exerciseId + "\",\"rmKg\":120}"))
                .andExpect(status().isOk());
        String sessionId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/pp/sessions", clubId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Force max\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(put("/clubs/{c}/pp/sessions/{s}/structure", clubId, sessionId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"structure":{"blocks":[
                              {"id":"b1","blockType":"PRINCIPAL","format":"CLASSIQUE","exercises":[
                                {"exerciseId":"%s","exerciseName":"Squat","setType":"STANDARD",
                                 "prescription":{"chargeRefType":"PCT_RM_RANGE","chargePctRmMin":80,"chargePctRmMax":85,
                                                 "sets":4,"repsFixed":5}}]}]}}""".formatted(exerciseId)))
                .andExpect(status().isOk());

        // Assignation au calendrier (preset AVANCE).
        String scheduledId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/pp/sessions/{s}/schedule", clubId, athleteId, sessionId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"date\":\"2026-06-25\",\"fieldsPreset\":\"AVANCE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // Prescription figée (snapshot + charges calculées + champs demandés).
        JsonNode presc = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/pp/scheduled/{id}/prescription", clubId, athleteId, scheduledId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(presc.get("snapshot").get("blocks")).hasSize(1);
        JsonNode charge = presc.get("calculated").get("blocks").get(0).get("exercises").get(0).get("charge");
        assertThat(charge.get("kgMin").asDouble()).isEqualTo(95.0);
        assertThat(charge.get("kgMax").asDouble()).isEqualTo(102.5);
        assertThat(presc.get("requiredFields").get("rir").asBoolean()).isTrue();   // preset AVANCE

        // L'athlète déplace puis renseigne fatigue/douleur.
        JsonNode moved = objectMapper.readTree(mvc.perform(
                        patch("/me/pp/scheduled/{id}/move", scheduledId)
                                .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scheduledDate\":\"2026-06-27\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(moved.get("movedByAthlete").asBoolean()).isTrue();
        assertThat(moved.get("originalDate").asText()).isEqualTo("2026-06-25");

        JsonNode fb = objectMapper.readTree(mvc.perform(
                        patch("/me/pp/scheduled/{id}/feedback", scheduledId)
                                .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"completed\":true,\"sessionRpe\":8,\"fatigue\":5,\"pain\":1}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(fb.get("completed").asBoolean()).isTrue();
        assertThat(fb.get("sessionFatigue").asInt()).isEqualTo(5);

        // Le calendrier athlète liste la séance déplacée.
        JsonNode cal = objectMapper.readTree(mvc.perform(
                        get("/me/pp/scheduled?from=2026-06-20&to=2026-06-30").header("Authorization", athleteBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(cal).hasSize(1);
        assertThat(cal.get(0).get("scheduledDate").asText()).isEqualTo("2026-06-27");
    }
}
