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

/**
 * Tests lactate bout-en-bout : détection temps réel, persistance avec seuils, et mise à jour
 * du profil physio de l'athlète.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LactateTestControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String bearer;
    private String clubId;
    private String athleteId;

    private static final String STEPS = """
            "steps":[
              {"speedMs":3.0,"lactate":1.0,"hr":130},
              {"speedMs":3.3,"lactate":1.2,"hr":140},
              {"speedMs":3.6,"lactate":1.8,"hr":150},
              {"speedMs":3.9,"lactate":3.0,"hr":160},
              {"speedMs":4.2,"lactate":5.5,"hr":170},
              {"speedMs":4.5,"lactate":8.0,"hr":178}]""";

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoSeedService.HEAD_COACH_EMAIL
                                + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        bearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();

        JsonNode athletes = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes?size=50", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        athleteId = athletes.get("content").get(0).get("id").asText();
    }

    @Test
    void detectsThresholdsInRealTime() throws Exception {
        JsonNode r = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/lactate-tests/detect", clubId, athleteId)
                                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"lactateRest\":0.8," + STEPS + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(r.get("lt1Threshold").asDouble()).isEqualTo(1.3);
        assertThat(r.get("lt1Ms").asDouble()).isBetween(3.3, 3.4);
        assertThat(r.get("lt2Ms").asDouble()).isEqualTo(3.9);
        assertThat(r.get("lt2Kmh").asDouble()).isCloseTo(14.04, org.assertj.core.data.Offset.offset(0.1));
        assertThat(r.get("fcLt2").asInt()).isEqualTo(160);
    }

    @Test
    void persistsTestAndUpdatesPhysioProfile() throws Exception {
        JsonNode created = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/lactate-tests", clubId, athleteId)
                                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"testDate\":\"2026-06-15\",\"lactateRest\":0.8," + STEPS + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(created.get("lt2Ms").asDouble()).isEqualTo(3.9);
        assertThat(created.get("steps")).hasSize(6);

        // Le profil physio de l'athlète a été mis à jour (applyToProfile par défaut).
        JsonNode profile = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/physio", clubId, athleteId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(profile.get("lt2Ms").asDouble()).isEqualTo(3.9);
        assertThat(profile.get("fcLt2").asInt()).isEqualTo(160);

        // Le test apparaît dans la liste.
        JsonNode list = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/lactate-tests", clubId, athleteId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(list).hasSize(1);
    }
}
