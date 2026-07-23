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
 * Chantier Z2 — valeurs de zones par athlète + pré-remplissage depuis le moteur physio.
 * Couvre : pré-remplissage AUTO au premier accès, resync, saisie manuelle (MANUAL) et verrou
 * préservés au resync.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AthleteZoneValueTest {

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

        // Profil physio complet → le moteur peut pré-remplir allure + FC.
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
    void firstAccessPrefillsAutoValuesFromEngine() throws Exception {
        JsonNode values = objectMapper.readTree(mvc.perform(get("/clubs/{c}/athletes/{a}/zone-values", clubId, athleteId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(values.size()).isGreaterThan(0);
        boolean hasPaceAndHr = false;
        for (JsonNode v : values) {
            assertThat(v.get("source").asText()).isEqualTo("AUTO");
            if (!v.get("valueMin").isNull() && !v.get("valueMax").isNull()) {
                hasPaceAndHr = true;
                // allure/FC : min < max (numériquement).
                assertThat(v.get("valueMin").asDouble()).isLessThanOrEqualTo(v.get("valueMax").asDouble());
            }
        }
        assertThat(hasPaceAndHr).isTrue();
    }

    @Test
    void manualValueAndLockSurviveResync() throws Exception {
        JsonNode values = objectMapper.readTree(mvc.perform(get("/clubs/{c}/athletes/{a}/zone-values", clubId, athleteId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode target = values.get(0);
        String zoneId = target.get("zoneId").asText();
        String metricId = target.get("metricTypeId").asText();

        // Saisie manuelle → bascule MANUAL.
        JsonNode manual = objectMapper.readTree(mvc.perform(
                        put("/clubs/{c}/athletes/{a}/zone-values/{z}/{m}", clubId, athleteId, zoneId, metricId)
                                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"valueMin\":245,\"valueMax\":250}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(manual.get("source").asText()).isEqualTo("MANUAL");
        assertThat(manual.get("valueMin").asDouble()).isEqualTo(245.0);

        // Resync : la valeur MANUAL n'est pas écrasée.
        mvc.perform(post("/clubs/{c}/athletes/{a}/zone-values/resync", clubId, athleteId)
                .header("Authorization", bearer)).andExpect(status().isOk());
        JsonNode afterResync = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/zone-values", clubId, athleteId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode v : afterResync) {
            if (v.get("zoneId").asText().equals(zoneId) && v.get("metricTypeId").asText().equals(metricId)) {
                assertThat(v.get("valueMin").asDouble()).isEqualTo(245.0);
                assertThat(v.get("source").asText()).isEqualTo("MANUAL");
            }
        }
    }
}
