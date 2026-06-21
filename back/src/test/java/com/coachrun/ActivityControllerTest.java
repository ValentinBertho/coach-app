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
