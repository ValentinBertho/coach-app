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

/** Tests 1RM (protocoles) : enregistrement, e1RM dérivé, profil mis à jour en {@code tested}. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StrengthTestTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String bearer;
    private String clubId;
    private String athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode coach = login(DemoSeedService.HEAD_COACH_EMAIL);
        bearer = "Bearer " + coach.get("accessToken").asText();
        clubId = coach.get("user").get("clubId").asText();
        athleteId = login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText();
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String createExercise() throws Exception {
        return objectMapper.readTree(mvc.perform(post("/clubs/{c}/pp/exercises", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Squat test\",\"category\":\"FORCE_MAX\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    @Test
    void trueOneRmTestUpdatesProfileAsTested() throws Exception {
        String exerciseId = createExercise();

        // TRUE_1RM : la charge soulevée 1 fois EST le 1RM.
        JsonNode test = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/pp/tests", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"" + exerciseId + "\",\"protocol\":\"TRUE_1RM\",\"weightKg\":120,\"reps\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(test.get("computedE1rmKg").asDouble()).isEqualTo(120.0);

        // Le profil 1RM passe en source 'tested' avec la valeur du test.
        JsonNode profile = objectMapper.readTree(mvc.perform(get("/clubs/{c}/athletes/{a}/pp/1rm", clubId, athleteId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode entry = null;
        for (JsonNode p : profile) {
            if (p.get("exerciseId").asText().equals(exerciseId)) entry = p;
        }
        assertThat(entry).isNotNull();
        assertThat(entry.get("source").asText()).isEqualTo("tested");
        assertThat(entry.get("rmKg").asDouble()).isEqualTo(120.0);
    }

    @Test
    void repTestDerivesE1rmViaNuzzo() throws Exception {
        String exerciseId = createExercise();

        // REP_TEST_3_5 : 100 kg × 5 reps → Nuzzo (≈ 108.84 kg).
        JsonNode test = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/pp/tests", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"" + exerciseId + "\",\"protocol\":\"REP_TEST_3_5\",\"weightKg\":100,\"reps\":5}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        // nuzzoPct1RM(5) = 91.8755 % → 100 / 0.918755 ≈ 108.84
        assertThat(test.get("computedE1rmKg").asDouble()).isBetween(108.0, 109.5);

        // Le test apparaît dans la liste.
        JsonNode list = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/pp/tests?exerciseId=" + exerciseId, clubId, athleteId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("protocol").asText()).isEqualTo("REP_TEST_3_5");
    }
}
