package com.coachrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ActivityControllerTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void importAutoMatchesPlannedWorkoutAndDeduplicates() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"act-%s@test.fr","password":"password123","fullName":"C","clubName":"AC %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String token = auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();

        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // séance prévue 10 km le 2026-07-01
        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"2026-07-01\",\"type\":\"ENDURANCE\",\"title\":\"10k\",\"targetDistanceM\":10000}"))
                .andExpect(status().isCreated());

        // import activité 10,2 km le même jour → MATCHED + delta +200
        mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"STRAVA\",\"externalId\":\"123\",\"activityDate\":\"2026-07-01\",\"distanceM\":10200,\"durationS\":2700}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.matchedWorkoutId").exists())
                .andExpect(jsonPath("$.distanceDeltaM").value(200));

        // ré-import même (source, externalId) → 409 dédup
        mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"STRAVA\",\"externalId\":\"123\",\"activityDate\":\"2026-07-01\",\"distanceM\":10200}"))
                .andExpect(status().isConflict());
    }

    @Test
    void matchedActivityIsExposedOnTheWorkout() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"act3-%s@test.fr","password":"password123","fullName":"C","clubName":"AC3 %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String token = auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        String workoutId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"2026-07-02\",\"type\":\"ENDURANCE\",\"title\":\"10k\",\"targetDistanceM\":10000,\"targetDurationS\":2600}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"STRAVA\",\"externalId\":\"456\",\"activityDate\":\"2026-07-02\",\"distanceM\":10300,\"durationS\":2700,\"avgHr\":152}"))
                .andExpect(status().isCreated());

        // La séance expose l'activité réalisée + les écarts prévu/réalisé.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/clubs/{c}/athletes/{a}/workouts/{w}/activity", clubId, athleteId, workoutId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceM").value(10300))
                .andExpect(jsonPath("$.avgHr").value(152))
                .andExpect(jsonPath("$.matchedWorkoutId").value(workoutId))
                .andExpect(jsonPath("$.distanceDeltaM").value(300))
                .andExpect(jsonPath("$.durationDeltaS").value(100));
    }

    @Test
    void workoutWithoutActivityReturnsNoContent() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"act4-%s@test.fr","password":"password123","fullName":"C","clubName":"AC4 %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String token = auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        String workoutId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"2026-07-03\",\"type\":\"ENDURANCE\",\"title\":\"EF\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/clubs/{c}/athletes/{a}/workouts/{w}/activity", clubId, athleteId, workoutId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void importWithoutMatchIsUnmatched() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"act2-%s@test.fr","password":"password123","fullName":"C","clubName":"AC2 %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String token = auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityDate\":\"2026-08-15\",\"distanceM\":8000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNMATCHED"));
    }
}
