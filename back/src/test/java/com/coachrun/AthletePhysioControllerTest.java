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
 * Bout-en-bout du profil physiologique : édition des seuils, ajout d'une performance et
 * recalcul automatique du VDOT + allures d'équivalence.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AthletePhysioControllerTest {

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

        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoSeedService.HEAD_COACH_EMAIL
                                + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        bearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();

        JsonNode athletes = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes?size=50", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        athleteId = athletes.get("content").get(0).get("id").asText();
    }

    @Test
    void updatesPhysioProfileAndExposesKmh() throws Exception {
        mvc.perform(put("/clubs/{c}/athletes/{a}/physio", clubId, athleteId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discipline\":\"TRAIL\",\"lt1Ms\":3.5,\"lt2Ms\":3.9,\"vcMs\":4.2,\"fcMax\":180}"))
                .andExpect(status().isOk());

        JsonNode profile = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/physio", clubId, athleteId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(profile.get("discipline").asText()).isEqualTo("TRAIL");
        assertThat(profile.get("lt2Ms").asDouble()).isEqualTo(3.9);
        // 3.9 m/s ≈ 14.04 km/h
        assertThat(profile.get("lt2Kmh").asDouble()).isCloseTo(14.0, org.assertj.core.data.Offset.offset(0.1));
        assertThat(profile.get("fcMax").asInt()).isEqualTo(180);
    }

    @Test
    void addingPerformanceRecomputesVdotAndPaces() throws Exception {
        // 5 km en 19:57 (1197 s) ≈ VDOT 50.
        mvc.perform(post("/clubs/{c}/athletes/{a}/performances", clubId, athleteId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distance\":\"D5KM\",\"timeSeconds\":1197}"))
                .andExpect(status().isCreated());

        JsonNode vdot = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/vdot", clubId, athleteId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(vdot.get("vdot").asDouble()).isBetween(49.0, 51.0);
        JsonNode paces = vdot.get("paces");
        assertThat(paces).hasSize(8);

        JsonNode fiveK = null;
        for (JsonNode p : paces) {
            if ("5km".equals(p.get("distance").asText())) {
                fiveK = p;
            }
        }
        assertThat(fiveK).isNotNull();
        // ~239 s/km (1197 / 5)
        assertThat(fiveK.get("paceSecPerKm").asInt()).isBetween(228, 250);
        assertThat(fiveK.get("paceLabel").asText()).contains(":");
    }

    @Test
    void sessionCalculatorReturnsRangesFromThresholds() throws Exception {
        // Profil avec seuils → calcul d'un bloc 95–102 % LT2 sans dépendre du VDOT.
        mvc.perform(put("/clubs/{c}/athletes/{a}/physio", clubId, athleteId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lt1Ms\":3.5,\"lt2Ms\":3.9,\"vcMs\":4.2,\"fcLt1\":148,\"fcLt2\":163,\"fcMax\":178}"))
                .andExpect(status().isOk());

        JsonNode calc = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/session-calc", clubId, athleteId)
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"ref\":\"PCT_LT2\",\"minPct\":95,\"maxPct\":102,\"reps\":4,\"distanceM\":1000}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(calc.get("computable").asBoolean()).isTrue();
        assertThat(calc.get("paceMinSecPerKm").asInt()).isLessThan(calc.get("paceMaxSecPerKm").asInt());
        assertThat(calc.get("hrMax").asInt()).isGreaterThan(calc.get("hrMin").asInt());
        assertThat(calc.get("estimatedDistanceM").asInt()).isEqualTo(4000);
    }

    @Test
    void listPerformancesReturnsComputedVdot() throws Exception {
        mvc.perform(post("/clubs/{c}/athletes/{a}/performances", clubId, athleteId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distance\":\"D10KM\",\"timeSeconds\":2480}"))
                .andExpect(status().isCreated());

        JsonNode list = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/performances", clubId, athleteId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(list).isNotEmpty();
        assertThat(list.get(0).get("vdot").asDouble()).isGreaterThan(0);
        assertThat(list.get(0).get("distanceCode").asText()).isEqualTo("10km");
    }
}
