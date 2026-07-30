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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chantier Z1 — catalogue de métriques + zones de travail paramétrables.
 * Couvre : catalogue global seedé, provisionnement paresseux du jeu de zones standard par club,
 * CRUD, réordonnancement et configuration des métriques portées par une zone.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TrainingZoneCatalogTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String bearer;
    private String clubId;

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
    }

    @Test
    void globalMetricCatalogIsSeeded() throws Exception {
        JsonNode list = objectMapper.readTree(mvc.perform(get("/clubs/{c}/metric-types", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        List<String> codes = new ArrayList<>();
        list.forEach(m -> codes.add(m.get("code").asText()));
        assertThat(codes).contains("PACE", "HR", "SPEED", "PCT_HRMAX", "POWER", "RPE");

        JsonNode pace = list.get(0);
        assertThat(pace.get("code").asText()).isEqualTo("PACE");
        assertThat(pace.get("unit").asText()).isEqualTo("S_PER_KM");
        assertThat(pace.get("direction").asText()).isEqualTo("LOWER_HARDER");
        assertThat(pace.get("builtin").asBoolean()).isTrue();
        assertThat(pace.get("clubId").isNull()).isTrue();
    }

    @Test
    void standardZonesAreSeededLazilyOnFirstList() throws Exception {
        JsonNode zones = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        // Deux échelles indépendantes : Allure (12 bandes) + Cardio (4 bandes).
        assertThat(zones).hasSize(16);
        assertThat(zones.get(0).get("name").asText()).isEqualTo("Footing facile");
        assertThat(zones.get(0).get("builtin").asBoolean()).isTrue();
        // Les zones d'allure portent l'allure + la vitesse (calibrée sur l'allure).
        assertThat(zones.get(0).get("metricTypeIds")).hasSize(2);
        // Les zones cardio portent la FC (bpm) + le % de FC max.
        assertThat(zones.get(12).get("name").asText()).isEqualTo("Endurance aérobie");
        assertThat(zones.get(12).get("metricTypeIds")).hasSize(2);

        // Idempotent : une 2ᵉ lecture ne re-seed pas.
        JsonNode again = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(again).hasSize(16);
    }

    @Test
    void zoneCrudAndReorder() throws Exception {
        // Seed initial via list.
        mvc.perform(get("/clubs/{c}/training-zones", clubId).header("Authorization", bearer))
                .andExpect(status().isOk());

        // Création d'une zone custom.
        JsonNode created = objectMapper.readTree(mvc.perform(post("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fartlek maison\",\"color\":\"#ff00aa\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String zoneId = created.get("id").asText();
        assertThat(created.get("builtin").asBoolean()).isFalse();
        assertThat(created.get("color").asText()).isEqualTo("#ff00aa");

        // Mise à jour.
        JsonNode updated = objectMapper.readTree(mvc.perform(put("/clubs/{c}/training-zones/{id}", clubId, zoneId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fartlek\",\"color\":\"#123456\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(updated.get("name").asText()).isEqualTo("Fartlek");

        // Liste : 16 standard + 1 custom.
        JsonNode zones = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(zones).hasSize(17);

        // Réordonnancement : on met la zone custom en tête.
        List<String> ids = new ArrayList<>();
        ids.add(zoneId);
        zones.forEach(z -> { if (!z.get("id").asText().equals(zoneId)) ids.add(z.get("id").asText()); });
        String orderedJson = objectMapper.writeValueAsString(ids);
        mvc.perform(patch("/clubs/{c}/training-zones/reorder", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":" + orderedJson + "}"))
                .andExpect(status().isNoContent());

        JsonNode reordered = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(reordered.get(0).get("id").asText()).isEqualTo(zoneId);

        // Suppression.
        mvc.perform(delete("/clubs/{c}/training-zones/{id}", clubId, zoneId)
                .header("Authorization", bearer)).andExpect(status().isNoContent());
    }

    @Test
    void zoneMetricsCanBeReconfigured() throws Exception {
        // Seed + récupère l'id de la métrique SPEED et d'une zone.
        JsonNode metrics = objectMapper.readTree(mvc.perform(get("/clubs/{c}/metric-types", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String speedId = null;
        String paceId = null;
        for (JsonNode m : metrics) {
            if ("SPEED".equals(m.get("code").asText())) speedId = m.get("id").asText();
            if ("PACE".equals(m.get("code").asText())) paceId = m.get("id").asText();
        }
        assertThat(speedId).isNotNull();

        JsonNode zones = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String zoneId = zones.get(4).get("id").asText(); // Tempo

        JsonNode result = objectMapper.readTree(mvc.perform(
                        put("/clubs/{c}/training-zones/{id}/metrics", clubId, zoneId)
                                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"metricTypeIds\":[\"" + paceId + "\",\"" + speedId + "\"]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        List<String> ids = new ArrayList<>();
        result.get("metricTypeIds").forEach(n -> ids.add(n.asText()));
        assertThat(ids).containsExactly(paceId, speedId);
    }

    @Test
    void addingAMetricKeepsExistingRules() throws Exception {
        JsonNode metrics = objectMapper.readTree(mvc.perform(get("/clubs/{c}/metric-types", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String rpeId = null, paceId = null, speedId = null;
        for (JsonNode m : metrics) {
            switch (m.get("code").asText()) {
                case "RPE" -> rpeId = m.get("id").asText();
                case "PACE" -> paceId = m.get("id").asText();
                case "SPEED" -> speedId = m.get("id").asText();
                default -> { }
            }
        }
        JsonNode zones = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode seuil2 = null;
        for (JsonNode z : zones) if ("Seuil 2 bas".equals(z.get("name").asText())) seuil2 = z;
        assertThat(seuil2).isNotNull();
        String zoneId = seuil2.get("id").asText();

        // Une zone d'allure porte l'allure + la vitesse ; le RPE n'est pas une zone (il se règle
        // sur le contenu de la séance).
        assertThat(seuil2.get("metricTypeIds")).hasSize(2);
        for (JsonNode r : seuil2.get("rules")) {
            assertThat(r.path("metricTypeId").asText()).isNotEqualTo(rpeId);
        }

        // Ajout d'une métrique supplémentaire sans retirer les autres : pas de 500 (contrainte
        // unique (zone, métrique)) ni d'effacement des règles existantes.
        JsonNode result = objectMapper.readTree(mvc.perform(
                        put("/clubs/{c}/training-zones/{id}/metrics", clubId, zoneId)
                                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"metricTypeIds\":[\"" + paceId + "\",\"" + speedId + "\",\""
                                        + rpeId + "\"]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        result.get("metricTypeIds").forEach(n -> ids.add(n.asText()));
        assertThat(ids).containsExactly(paceId, speedId, rpeId);

        // La règle allure de la zone est préservée après l'ajout : on vérifie qu'elle porte
        // toujours une ancre et une bande, sans figer les pourcentages de l'échelle seedée.
        boolean paceRuleKept = false;
        for (JsonNode r : result.get("rules")) {
            if (paceId.equals(r.path("metricTypeId").asText()) && !r.path("anchor").isNull()) {
                paceRuleKept = true;
                assertThat(r.get("lowPct").asDouble()).isGreaterThan(0);
                assertThat(r.get("highPct").asDouble()).isGreaterThan(r.get("lowPct").asDouble());
            }
        }
        assertThat(paceRuleKept).isTrue();
    }
}
