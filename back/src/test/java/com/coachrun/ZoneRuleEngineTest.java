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

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chantier zones v2 — phase 1 : règles de calcul (ancre + %) éditables sur les zones,
 * calcul data-driven des valeurs, et recalcul automatique quand une ancre de l'athlète change.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ZoneRuleEngineTest {

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
    }

    @Test
    void zonesCarryEditableRules() throws Exception {
        JsonNode zones = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        // « Seuil 2 bas » s'arrête pile à LT2 : c'est la zone juste sous le seuil lactique.
        JsonNode seuil = null;
        for (JsonNode z : zones) if ("Seuil 2 bas".equals(z.get("name").asText())) seuil = z;
        assertThat(seuil).isNotNull();
        boolean hasLt2 = false;
        for (JsonNode r : seuil.get("rules")) {
            if ("LT2".equals(r.path("anchor").asText())) {
                hasLt2 = true;
                assertThat(r.get("lowPct").asDouble()).isEqualTo(97.0);
                assertThat(r.get("highPct").asDouble()).isEqualTo(100.0);
            }
        }
        assertThat(hasLt2).isTrue();
    }

    @Test
    void autoRecomputeOnAnchorChange() throws Exception {
        String hrId = metricId("HR");
        // Pré-remplissage initial.
        JsonNode before = objectMapper.readTree(mvc.perform(get("/clubs/{c}/athletes/{a}/zone-values", clubId, athleteId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        double hrMaxValBefore = maxHrValue(before, hrId);
        assertThat(hrMaxValBefore).isGreaterThan(0);

        // Change une ancre (FC max 185 → 200) → recalcul AUTO automatique (sans bouton resync).
        mvc.perform(put("/clubs/{c}/athletes/{a}/physio", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lt1Ms\":3.5,\"lt2Ms\":3.9,\"vcMs\":4.2,\"fcLt1\":148,\"fcLt2\":163,\"fcMax\":200}"))
                .andExpect(status().isOk());

        JsonNode after = objectMapper.readTree(mvc.perform(get("/clubs/{c}/athletes/{a}/zone-values", clubId, athleteId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        // Les FC de zone (calculées en % de FCmax) ont augmenté avec la FCmax.
        assertThat(maxHrValue(after, hrId)).isGreaterThan(hrMaxValBefore);
    }

    private String metricId(String code) throws Exception {
        JsonNode metrics = objectMapper.readTree(mvc.perform(get("/clubs/{c}/metric-types", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode m : metrics) if (code.equals(m.get("code").asText())) return m.get("id").asText();
        return null;
    }

    private double maxHrValue(JsonNode values, String hrId) {
        double max = 0;
        for (JsonNode v : values) {
            if (hrId.equals(v.get("metricTypeId").asText()) && !v.get("valueMax").isNull()) {
                max = Math.max(max, v.get("valueMax").asDouble());
            }
        }
        return max;
    }

    @Test
    void editingRuleThenResyncChangesValues() throws Exception {
        // id métrique HR + une zone.
        JsonNode metrics = objectMapper.readTree(mvc.perform(get("/clubs/{c}/metric-types", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String hrId = null;
        for (JsonNode m : metrics) if ("HR".equals(m.get("code").asText())) hrId = m.get("id").asText();

        JsonNode zones = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        String zoneId = null;
        for (JsonNode z : zones) if ("Endurance aérobie".equals(z.get("name").asText())) zoneId = z.get("id").asText();

        // Édite la règle HR → 50–60 % FCmax.
        mvc.perform(put("/clubs/{c}/training-zones/{z}/metrics/{m}/rule", clubId, zoneId, hrId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anchor\":\"FCMAX\",\"lowPct\":50,\"highPct\":60,\"model\":\"PCT_FCMAX\"}"))
                .andExpect(status().isOk());

        // Resync → la FC de cette zone devient 50–60 % de 185 ≈ 93–111.
        mvc.perform(post("/clubs/{c}/athletes/{a}/zone-values/resync", clubId, athleteId)
                .header("Authorization", bearer)).andExpect(status().isOk());
        JsonNode values = objectMapper.readTree(mvc.perform(get("/clubs/{c}/athletes/{a}/zone-values", clubId, athleteId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode v : values) {
            if (v.get("zoneId").asText().equals(zoneId) && v.get("metricTypeId").asText().equals(hrId)) {
                found = true;
                assertThat(v.get("valueMin").asDouble()).isEqualTo(Math.round(185 * 0.50));
                assertThat(v.get("valueMax").asDouble()).isEqualTo(Math.round(185 * 0.60));
            }
        }
        assertThat(found).isTrue();
    }

    /**
     * L'échelle d'allure est une <b>chaîne contiguë</b> : la borne rapide d'une zone est exactement
     * la borne lente de la suivante, du footing facile au 800 m. Sans quoi un athlète se retrouve
     * dans deux zones à la fois, ou dans aucune.
     */
    @Test
    void paceScaleIsContiguous() throws Exception {
        String paceMetricId = metricId("PACE");
        JsonNode values = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/zone-values", clubId, athleteId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        // Bandes d'allure de l'athlète, indexées par zone.
        java.util.Map<String, int[]> byZone = new java.util.HashMap<>();
        for (JsonNode v : values) {
            if (paceMetricId.equals(v.get("metricTypeId").asText())) {
                byZone.put(v.get("zoneId").asText(),
                        new int[]{v.get("valueMin").asInt(), v.get("valueMax").asInt()});
            }
        }

        // La chaîne va du footing facile au seuil 2 haut : ce sont les zones ancrées LT1/LT2. Les
        // allures de compétition suivent les records (ancres VDOT) et restent hors chaîne à dessein.
        List<String> scale = List.of("Footing facile", "EF", "Steady", "Seuil 1", "Tempo",
                "Seuil 2 bas", "Seuil 2 haut");
        int[] previous = null;
        for (String name : scale) {
            int[] band = byZone.get(zoneIdNamed(name));
            assertThat(band).as("bande d'allure de « %s »", name).isNotNull();
            // valueMin = allure rapide (s/km), valueMax = allure lente.
            assertThat(band[0]).as("« %s » : borne rapide < borne lente", name).isLessThan(band[1]);
            if (previous != null) {
                // La borne rapide de la zone précédente est la borne lente de celle-ci : ±1 s
                // d'arrondi, puisque les deux viennent d'un calcul en secondes entières.
                assertThat(band[1]).as("« %s » démarre où la précédente s'arrête", name)
                        .isBetween(previous[0] - 1, previous[0] + 1);
            }
            previous = band;
        }
    }

    /**
     * La zone qui enjambe la frontière LT1 → LT2 exprime sa borne basse en % de LT1 et sa borne
     * haute en % de LT2 : c'est la seule façon de coller à ses deux voisines quel que soit le
     * rapport LT1/LT2 de l'athlète.
     */
    @Test
    void transitionZoneBridgesLt1AndLt2() throws Exception {
        JsonNode zones = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        JsonNode seuil1 = null;
        for (JsonNode z : zones) if ("Seuil 1".equals(z.get("name").asText())) seuil1 = z;
        assertThat(seuil1).isNotNull();

        boolean bridged = false;
        for (JsonNode r : seuil1.get("rules")) {
            if ("LT1".equals(r.path("anchor").asText()) && "LT2".equals(r.path("highAnchor").asText())) {
                bridged = true;
                assertThat(r.get("lowPct").asDouble()).isEqualTo(100.0);  // pile à LT1
                assertThat(r.get("highPct").asDouble()).isEqualTo(93.0);  // 93 % de LT2
            }
        }
        assertThat(bridged).as("« Seuil 1 » enjambe LT1 → LT2").isTrue();
    }

    /**
     * Double échelle : les zones d'allure ne portent pas de FC. Un bloc peut donc désigner une
     * <b>zone cardio</b> en second ({@code hrZoneId}) pour obtenir allure <i>et</i> FC.
     */
    @Test
    void blockCombinesPaceZoneWithCardioZone() throws Exception {
        String paceZoneId = zoneIdNamed("Seuil 2 bas");
        String cardioZoneId = zoneIdNamed("Seuil");

        // Zone d'allure seule : une cible d'allure, pas de FC.
        JsonNode paceOnly = calcBlock("{\"zoneId\":\"" + paceZoneId + "\",\"distanceM\":1000}");
        assertThat(paceOnly.get("computable").asBoolean()).isTrue();
        assertThat(paceOnly.get("paceMinSecPerKm").isNull()).isFalse();
        assertThat(paceOnly.get("hrMin").isNull()).isTrue();

        // Même zone d'allure + zone cardio : l'allure est inchangée, la FC vient de la zone cardio.
        JsonNode both = calcBlock("{\"zoneId\":\"" + paceZoneId + "\",\"hrZoneId\":\"" + cardioZoneId
                + "\",\"distanceM\":1000}");
        assertThat(both.get("paceMinSecPerKm").asInt()).isEqualTo(paceOnly.get("paceMinSecPerKm").asInt());
        // Bande « Seuil » = 80–90 % de FCmax 185.
        assertThat(both.get("hrMin").asInt()).isEqualTo((int) Math.round(185 * 0.80));
        assertThat(both.get("hrMax").asInt()).isEqualTo((int) Math.round(185 * 0.90));
    }

    private JsonNode calcBlock(String body) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/session-calc", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String zoneIdNamed(String name) throws Exception {
        JsonNode zones = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (JsonNode z : zones) {
            if (name.equals(z.get("name").asText())) {
                return z.get("id").asText();
            }
        }
        throw new AssertionError("Zone « " + name + " » absente du seed.");
    }
}
