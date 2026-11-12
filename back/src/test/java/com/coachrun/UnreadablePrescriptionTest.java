package com.coachrun;

import com.coachrun.entity.Workout;
import com.coachrun.repository.WorkoutRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Une prescription enregistrée mais illisible n'est pas une séance vide.
 *
 * <p>Elle était rendue comme telle, sans trace : le coach y voyait un oubli, l'athlète un repos.
 * La réponse porte maintenant {@code unreadable}, que l'écran affiche.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UnreadablePrescriptionTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private WorkoutRepository workoutRepository;

    private MockMvc mvc;
    private String bearer;
    private String clubId;
    private String athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode auth = login(DemoSeedService.HEAD_COACH_EMAIL);
        bearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();
        athleteId = login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText();
    }

    @Test
    void aReadablePrescriptionIsNotFlagged() throws Exception {
        String workoutId = plannedWorkoutWithStructure();
        JsonNode p = prescription(workoutId);
        assertThat(p.get("unreadable").asBoolean()).isFalse();
        assertThat(p.get("snapshot").get("main")).hasSize(1);
    }

    @Test
    void aCorruptedPrescriptionIsFlaggedInsteadOfLookingEmpty() throws Exception {
        String workoutId = plannedWorkoutWithStructure();
        Workout w = workoutRepository.findById(UUID.fromString(workoutId)).orElseThrow();
        w.setSessionSnapshot("{\"main\": [ {\"id\": \"b1\", \"type\"");
        workoutRepository.saveAndFlush(w);

        JsonNode p = prescription(workoutId);
        assertThat(p.get("unreadable").asBoolean()).isTrue();
        assertThat(p.get("snapshot").get("main")).isEmpty();
    }

    @Test
    void aSessionWithoutPrescriptionIsNotFlagged() throws Exception {
        String workoutId = plannedWorkout();
        assertThat(prescription(workoutId).get("unreadable").asBoolean()).isFalse();
    }

    private JsonNode prescription(String workoutId) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workouts/{w}/prescription", clubId, athleteId, workoutId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String plannedWorkout() throws Exception {
        return objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "scheduledDate", LocalDate.now().toString(),
                                        "type", "INTERVALS",
                                        "title", "6 x 1000 m",
                                        "targetDurationS", 3600))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    private String plannedWorkoutWithStructure() throws Exception {
        String workoutId = plannedWorkout();
        mvc.perform(put("/clubs/{c}/athletes/{a}/workouts/{w}/structure", clubId, athleteId, workoutId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "warmup", List.of(),
                                "main", List.of(Map.of(
                                        "id", "b1", "type", "intervals", "reps", 6, "distanceM", 1000)),
                                "cooldown", List.of()))))
                .andExpect(status().isOk());
        return workoutId;
    }
}
