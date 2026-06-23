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
 * Calendrier DARI Lab : assignation d'une séance de bibliothèque (snapshot figé + cibles
 * calculées), déplacement par l'athlète (jamais modification), feedback fatigue/douleur.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CalendarSchedulingTest {

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
    void scheduleFromLibrarySnapshotsAndAthleteMovesAndGivesFeedback() throws Exception {
        // 1. Profil + perf (allures VDOT) pour l'athlète démo.
        mvc.perform(put("/clubs/{c}/athletes/{a}/physio", clubId, athleteId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lt1Ms\":3.5,\"lt2Ms\":3.9,\"vcMs\":4.2,\"fcLt1\":148,\"fcLt2\":163,\"fcMax\":178}"))
                .andExpect(status().isOk());
        mvc.perform(post("/clubs/{c}/athletes/{a}/performances", clubId, athleteId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distance\":\"D5KM\",\"timeSeconds\":1197}"))
                .andExpect(status().isCreated());

        // 2. Modèle + structure (6 × 1000 m à 98–103 % allure 5 km).
        String templateId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/workout-templates", clubId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VMA 6x1000\",\"type\":\"INTERVALS\",\"title\":\"VMA\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        mvc.perform(put("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"discipline":"ROUTE","structure":{
                              "main":[{"type":"intervals","reps":6,"distanceM":1000,
                                       "prescription":{"ref":"PCT_PACE_5KM","minPct":98,"maxPct":103}}]}}"""))
                .andExpect(status().isOk());

        // 3. Assignation au calendrier de l'athlète.
        JsonNode scheduled = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workout-templates/{t}/schedule", clubId, athleteId, templateId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"date\":\"2026-06-25\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String workoutId = scheduled.get("id").asText();
        assertThat(scheduled.get("sourceTemplateId").asText()).isEqualTo(templateId);
        assertThat(scheduled.get("movedByAthlete").asBoolean()).isFalse();

        // 4. Prescription figée + cibles calculées (vue coach).
        JsonNode presc = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workouts/{w}/prescription", clubId, athleteId, workoutId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(presc.get("snapshot").get("main")).hasSize(1);
        assertThat(presc.get("calculated").get("main").get(0).get("calc").get("estimatedDistanceM").asInt())
                .isEqualTo(6000);

        // 5. L'athlète déplace la séance (date uniquement) → marqué movedByAthlete, date d'origine conservée.
        JsonNode moved = objectMapper.readTree(mvc.perform(
                        patch("/me/workouts/{w}/move", workoutId)
                                .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scheduledDate\":\"2026-06-27\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(moved.get("movedByAthlete").asBoolean()).isTrue();
        assertThat(moved.get("originalDate").asText()).isEqualTo("2026-06-25");
        assertThat(moved.get("scheduledDate").asText()).isEqualTo("2026-06-27");

        // 6. Feedback fatigue/douleur (base de l'état de forme).
        JsonNode fb = objectMapper.readTree(mvc.perform(
                        patch("/me/workouts/{w}/feedback", workoutId)
                                .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"COMPLETED\",\"rpe\":7,\"fatigue\":6,\"pain\":2,\"comment\":\"ok\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(fb.get("fatigue").asInt()).isEqualTo(6);
        assertThat(fb.get("pain").asInt()).isEqualTo(2);

        // 7. L'athlète voit la prescription calculée de sa séance.
        mvc.perform(get("/me/workouts/{w}/prescription", workoutId)
                        .header("Authorization", athleteBearer))
                .andExpect(status().isOk());
    }
}
