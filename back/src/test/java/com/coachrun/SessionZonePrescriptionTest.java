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
 * Chantier Z3 — prescription par zone (lecture directe des valeurs athlète) + non-régression du
 * chemin legacy (référentiel + fourchette %).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SessionZonePrescriptionTest {

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
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        bearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();
        JsonNode athletes = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes?size=50", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        athleteId = athletes.get("content").get(0).get("id").asText();
        mvc.perform(put("/clubs/{c}/athletes/{a}/physio", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lt1Ms\":3.5,\"lt2Ms\":3.9,\"vcMs\":4.2,\"fcLt1\":148,\"fcLt2\":163,\"fcMax\":185}"))
                .andExpect(status().isOk());
        mvc.perform(post("/clubs/{c}/athletes/{a}/performances", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distance\":\"D5KM\",\"timeSeconds\":1197}"))
                .andExpect(status().isCreated());
    }

    @Test
    void zonePrescriptionReadsTargetFromAthleteValues() throws Exception {
        // Métrique PACE.
        JsonNode metrics = objectMapper.readTree(mvc.perform(get("/clubs/{c}/metric-types", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String paceId = null;
        for (JsonNode m : metrics) if ("PACE".equals(m.get("code").asText())) paceId = m.get("id").asText();

        // Valeurs pré-remplies : on prend une zone qui a une allure.
        JsonNode values = objectMapper.readTree(mvc.perform(get("/clubs/{c}/athletes/{a}/zone-values", clubId, athleteId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String zoneId = null;
        double paceMin = 0, paceMax = 0;
        for (JsonNode v : values) {
            if (v.get("metricTypeId").asText().equals(paceId) && !v.get("valueMin").isNull()) {
                zoneId = v.get("zoneId").asText();
                paceMin = v.get("valueMin").asDouble();
                paceMax = v.get("valueMax").asDouble();
                break;
            }
        }
        assertThat(zoneId).isNotNull();

        // Calcul par zone : la cible est lue depuis la fiche athlète, pas via base × %.
        JsonNode calc = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/session-calc", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneId\":\"" + zoneId + "\",\"reps\":5,\"distanceM\":1000}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(calc.get("computable").asBoolean()).isTrue();
        assertThat(calc.get("paceMinSecPerKm").asInt()).isEqualTo((int) Math.round(paceMin));
        assertThat(calc.get("paceMaxSecPerKm").asInt()).isEqualTo((int) Math.round(paceMax));
        assertThat(calc.get("estimatedDistanceM").asInt()).isEqualTo(5000);
        assertThat(calc.get("ref").isNull()).isTrue(); // chemin zone : pas de référentiel legacy.
    }

    @Test
    void legacyRangePrescriptionStillComputes() throws Exception {
        JsonNode calc = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/session-calc", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ref\":\"PCT_PACE_5KM\",\"minPct\":98,\"maxPct\":103,\"reps\":6,\"distanceM\":1000}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(calc.get("computable").asBoolean()).isTrue();
        assertThat(calc.get("ref").asText()).isEqualTo("PCT_PACE_5KM");
        assertThat(calc.get("estimatedDistanceM").asInt()).isEqualTo(6000);
    }

    @Test
    void invalidPrescriptionIsRejected() throws Exception {
        // Ni zone ni fourchette complète → 400.
        mvc.perform(post("/clubs/{c}/athletes/{a}/session-calc", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reps\":4,\"distanceM\":400}"))
                .andExpect(status().isBadRequest());
    }
}
