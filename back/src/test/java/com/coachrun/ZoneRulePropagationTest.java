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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Changer le pourcentage d'une zone doit changer ce qui est prescrit.
 *
 * <p>La règle bougeait sans que rien ne suive : les allures et fréquences cardiaques déjà calculées
 * restaient en place jusqu'à ce que quelqu'un pense à ouvrir la fiche de chaque athlète et à
 * cliquer « Resynchroniser ». Le coach modifiait « 88–92 % », sa séance continuait d'annoncer
 * l'ancienne allure, et rien ne disait pourquoi.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ZoneRulePropagationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String coachBearer;
    private String clubId;
    private String athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode coach = login(DemoSeedService.HEAD_COACH_EMAIL);
        coachBearer = "Bearer " + coach.get("accessToken").asText();
        clubId = coach.get("user").get("clubId").asText();
        athleteId = login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText();
    }

    /** Le cas du coach : il décale un seuil, la valeur de l'athlète suit. */
    @Test
    void changingAPercentageMovesTheAthleteValue() throws Exception {
        JsonNode target = firstRuledZoneMetric();
        String zoneId = target.get("zoneId").asText();
        String metricId = target.get("metricTypeId").asText();

        double before = valueMaxOf(zoneId, metricId);

        // On élargit la borne haute de dix points : la valeur dérivée ne peut pas rester la même.
        putRule(zoneId, metricId, target, target.get("lowPct").asDouble(),
                target.get("highPct").asDouble() + 10);

        assertThat(valueMaxOf(zoneId, metricId))
                .as("la valeur de l'athlete doit suivre la regle, sans resynchronisation manuelle")
                .isNotEqualTo(before);
    }

    /** Et un ajustement posé à la main sur un athlète survit : c'était le sens du verrou. */
    @Test
    void aLockedValueSurvivesTheChange() throws Exception {
        JsonNode target = firstRuledZoneMetric();
        String zoneId = target.get("zoneId").asText();
        String metricId = target.get("metricTypeId").asText();

        mvc.perform(put("/clubs/{c}/athletes/{a}/zone-values/{z}/{m}", clubId, athleteId, zoneId, metricId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("valueMin", 300.0, "valueMax", 320.0, "locked", true))))
                .andExpect(status().isOk());

        putRule(zoneId, metricId, target, target.get("lowPct").asDouble(),
                target.get("highPct").asDouble() + 10);

        assertThat(valueMaxOf(zoneId, metricId))
                .as("une valeur verrouillee ne doit pas etre ecrasee par le changement d'echelle")
                .isEqualTo(320.0);
    }

    // --- Utilitaires ---------------------------------------------------------------------------

    /** Première (zone, métrique) portant une règle complète, et de quoi la rejouer. */
    private JsonNode firstRuledZoneMetric() throws Exception {
        JsonNode zones = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode z : zones) {
            for (JsonNode r : z.get("rules")) {
                if (!r.get("anchor").isNull() && !r.get("lowPct").isNull() && !r.get("highPct").isNull()) {
                    return objectMapper.createObjectNode()
                            .put("zoneId", z.get("id").asText())
                            .put("metricTypeId", r.get("metricTypeId").asText())
                            .put("anchor", r.get("anchor").asText())
                            .put("lowPct", r.get("lowPct").asDouble())
                            .put("highPct", r.get("highPct").asDouble());
                }
            }
        }
        throw new AssertionError("aucune zone reglee dans le jeu de demonstration");
    }

    private void putRule(String zoneId, String metricId, JsonNode base, double low, double high)
            throws Exception {
        var body = objectMapper.createObjectNode()
                .put("anchor", base.get("anchor").asText())
                .put("lowPct", low)
                .put("highPct", high)
                .put("model", "CUSTOM");
        body.putNull("highAnchor");
        mvc.perform(put("/clubs/{c}/training-zones/{z}/metrics/{m}/rule", clubId, zoneId, metricId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private double valueMaxOf(String zoneId, String metricId) throws Exception {
        JsonNode values = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/zone-values", clubId, athleteId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode v : values) {
            if (zoneId.equals(v.get("zoneId").asText()) && metricId.equals(v.get("metricTypeId").asText())) {
                return v.get("valueMax").isNull() ? Double.NaN : v.get("valueMax").asDouble();
            }
        }
        return Double.NaN;
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
