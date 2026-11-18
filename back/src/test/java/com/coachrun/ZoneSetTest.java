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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Modèles de zones : plusieurs échelles par club, appliquées athlète par athlète.
 * Le jeu par défaut est provisionné paresseusement et adopte les zones déjà en place.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ZoneSetTest {

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

        JsonNode athletes = objectMapper.readTree(mvc.perform(get("/clubs/{c}/athletes?size=50", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        athleteId = athletes.get("content").get(0).get("id").asText();
    }

    /** Le club existant hérite d'un jeu par défaut qui porte toutes ses zones. */
    @Test
    void defaultSetAdoptsExistingZones() throws Exception {
        JsonNode zones = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode sets = objectMapper.readTree(mvc.perform(get("/clubs/{c}/zone-sets", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        // Le jeu par défaut, puis les deux modèles de référence (Vitesses Critiques, VMA).
        assertThat(sets).hasSize(3);
        assertThat(sets.get(0).get("isDefault").asBoolean()).isTrue();
        assertThat(sets.get(0).get("zoneCount").asInt()).isEqualTo(zones.size());
        assertThat(zones.size()).isGreaterThan(0);
    }

    /**
     * Chaque club reçoit, une fois, les modèles « Vitesses Critiques » et « VMA » du coach pilote :
     * 16 zones chacun, ancrées sur la VC ou la VMA. Un modèle supprimé ne revient pas.
     */
    @Test
    void referenceModelsAreOfferedOnce() throws Exception {
        JsonNode sets = objectMapper.readTree(mvc.perform(get("/clubs/{c}/zone-sets", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String vcId = null;
        String vmaId = null;
        for (JsonNode s : sets) {
            if (s.get("name").asText().equals("Zones Vitesses Critiques")) {
                vcId = s.get("id").asText();
                assertThat(s.get("zoneCount").asInt()).isEqualTo(16);
            }
            if (s.get("name").asText().equals("Zones VMA")) {
                vmaId = s.get("id").asText();
                assertThat(s.get("zoneCount").asInt()).isEqualTo(16);
            }
        }
        assertThat(vcId).isNotNull();
        assertThat(vmaId).isNotNull();

        JsonNode vcZones = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones?setId=" + vcId, clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(vcZones.get(0).get("name").asText()).isEqualTo("Récupération");
        assertThat(vcZones.toString()).contains("\"anchor\":\"VC\"").contains("PACE_10KM");

        mvc.perform(delete("/clubs/{c}/zone-sets/{id}", clubId, vmaId).header("Authorization", bearer))
                .andExpect(status().is2xxSuccessful());
        JsonNode again = objectMapper.readTree(mvc.perform(get("/clubs/{c}/zone-sets", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(again).hasSize(2);
        assertThat(again.toString()).doesNotContain("Zones VMA");
    }

    /**
     * Un second modèle recopie l'échelle du jeu par défaut, s'applique à un athlète, et c'est
     * bien ce jeu-là que la fiche athlète expose ensuite.
     */
    @Test
    void secondSetIsCopiedAndAppliedToAthlete() throws Exception {
        JsonNode created = objectMapper.readTree(mvc.perform(post("/clubs/{c}/zone-sets", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Trail\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String setId = created.get("id").asText();
        assertThat(created.get("isDefault").asBoolean()).isFalse();
        assertThat(created.get("zoneCount").asInt()).isGreaterThan(0);

        // Les zones du nouveau jeu sont distinctes de celles du jeu par défaut.
        JsonNode copied = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/training-zones?setId={s}", clubId, setId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode defaults = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(copied.get(0).get("id").asText()).isNotEqualTo(defaults.get(0).get("id").asText());
        assertThat(copied.get(0).get("name").asText()).isEqualTo(defaults.get(0).get("name").asText());

        // Application à l'athlète.
        mvc.perform(put("/clubs/{c}/athletes/{a}/zone-set", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneSetId\":\"" + setId + "\"}"))
                .andExpect(status().isNoContent());

        JsonNode current = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/zone-set", clubId, athleteId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(current.get("zoneSetId").asText()).isEqualTo(setId);

        // Les zones vues depuis l'athlète sont celles de son modèle.
        JsonNode forAthlete = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/training-zones?athleteId={a}", clubId, athleteId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(forAthlete.get(0).get("id").asText()).isEqualTo(copied.get(0).get("id").asText());

        // Retour au jeu par défaut.
        mvc.perform(put("/clubs/{c}/athletes/{a}/zone-set", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneSetId\":null}"))
                .andExpect(status().isNoContent());
        JsonNode back = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/training-zones?athleteId={a}", clubId, athleteId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(back.get(0).get("id").asText()).isEqualTo(defaults.get(0).get("id").asText());
    }

    /** Le jeu par défaut n'est pas supprimable : les athlètes sans modèle s'y rabattent. */
    @Test
    void defaultSetCannotBeDeleted() throws Exception {
        JsonNode sets = objectMapper.readTree(mvc.perform(get("/clubs/{c}/zone-sets", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String defaultId = sets.get(0).get("id").asText();

        mvc.perform(delete("/clubs/{c}/zone-sets/{id}", clubId, defaultId).header("Authorization", bearer))
                .andExpect(status().isConflict());
    }

    private String setIdNamed(String name) throws Exception {
        JsonNode sets = objectMapper.readTree(mvc.perform(get("/clubs/{c}/zone-sets", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode s : sets) {
            if (s.get("name").asText().equals(name)) {
                return s.get("id").asText();
            }
        }
        throw new AssertionError("Modèle introuvable : " + name);
    }

    private String zoneIdNamed(JsonNode zones, String name) {
        for (JsonNode z : zones) {
            if (z.get("name").asText().equals(name)) {
                return z.get("id").asText();
            }
        }
        throw new AssertionError("Zone introuvable : " + name);
    }

    /**
     * Une séance écrite sur le modèle par défaut (« Footing facile ») se lit chez un athlète en
     * Vitesses Critiques sur sa zone homologue « Récupération » : même cible, au lieu de rien.
     */
    @Test
    void lowestZoneIsMatchedAcrossModels() throws Exception {
        String vcId = setIdNamed("Zones Vitesses Critiques");
        mvc.perform(put("/clubs/{c}/athletes/{a}/zone-set", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneSetId\":\"" + vcId + "\"}"))
                .andExpect(status().isNoContent());

        JsonNode defaults = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode vcZones = objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones?setId=" + vcId, clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        String footing = zoneIdNamed(defaults, "Footing facile");
        String recup = zoneIdNamed(vcZones, "Récupération");
        String viaDefault = mvc.perform(post("/clubs/{c}/athletes/{a}/session-calc", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneId\":\"" + footing + "\",\"durationS\":1200}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String direct = mvc.perform(post("/clubs/{c}/athletes/{a}/session-calc", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneId\":\"" + recup + "\",\"durationS\":1200}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(viaDefault)).isEqualTo(objectMapper.readTree(direct));

        // Les valeurs de l'athlète portent le nom de zone de SON modèle (rapprochement côté éditeur).
        JsonNode values = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/zone-values", clubId, athleteId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(values.size()).isGreaterThan(0);
        assertThat(values.toString()).contains("\"zoneName\":\"Récupération\"")
                .doesNotContain("\"zoneName\":\"Footing facile\"");
    }

    /**
     * Changer le modèle par défaut du club bascule les athlètes qui n'ont pas de modèle choisi ;
     * ceux qui en ont un le gardent. L'ancien défaut devient un modèle comme un autre.
     */
    @Test
    void defaultSetCanBeChanged() throws Exception {
        String oldDefault = setIdNamed("Zones par défaut");
        String vcId = setIdNamed("Zones Vitesses Critiques");
        String vmaId = setIdNamed("Zones VMA");

        JsonNode athletes = objectMapper.readTree(mvc.perform(get("/clubs/{c}/athletes?size=50", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String pinned = athletes.get("content").get(1).get("id").asText();
        mvc.perform(put("/clubs/{c}/athletes/{a}/zone-set", clubId, pinned)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneSetId\":\"" + vmaId + "\"}"))
                .andExpect(status().isNoContent());

        JsonNode sets = objectMapper.readTree(mvc.perform(put("/clubs/{c}/zone-sets/{id}/default", clubId, vcId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode s : sets) {
            assertThat(s.get("isDefault").asBoolean()).isEqualTo(s.get("id").asText().equals(vcId));
        }

        JsonNode forAthlete = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/training-zones?athleteId={a}", clubId, athleteId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(forAthlete.get(0).get("name").asText()).isEqualTo("Récupération");
        JsonNode values = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/zone-values", clubId, athleteId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(values.toString()).doesNotContain("\"zoneName\":\"Footing facile\"");

        JsonNode pinnedSet = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/zone-set", clubId, pinned).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(pinnedSet.get("zoneSetId").asText()).isEqualTo(vmaId);

        // L'ancien défaut, désormais ordinaire, devient supprimable ; le nouveau ne l'est plus.
        mvc.perform(delete("/clubs/{c}/zone-sets/{id}", clubId, vcId).header("Authorization", bearer))
                .andExpect(status().isConflict());
        mvc.perform(delete("/clubs/{c}/zone-sets/{id}", clubId, oldDefault).header("Authorization", bearer))
                .andExpect(status().is2xxSuccessful());
    }

    /** L'aperçu d'un modèle pour un athlète calcule ses cibles sans rien écrire sur sa fiche. */
    @Test
    void previewComputesTargetsWithoutWriting() throws Exception {
        String defaultId = setIdNamed("Zones par défaut");
        String vcId = setIdNamed("Zones Vitesses Critiques");
        String before = mvc.perform(get("/clubs/{c}/athletes/{a}/zone-values", clubId, athleteId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode own = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/zone-sets/{id}/preview/{a}", clubId, defaultId, athleteId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(own.get("athleteUsesThisSet").asBoolean()).isTrue();

        JsonNode vc = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/zone-sets/{id}/preview/{a}", clubId, vcId, athleteId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(vc.get("athleteUsesThisSet").asBoolean()).isFalse();
        if (!vc.get("references").get("vcPaceS").isNull()) {
            // Allure : la borne basse est la plus rapide (moins de secondes au km).
            JsonNode pace = null;
            for (JsonNode v : vc.get("values")) {
                if ("PACE".equals(v.get("metricCode").asText())) {
                    pace = v;
                    break;
                }
            }
            assertThat(pace).isNotNull();
            assertThat(pace.get("min").asDouble()).isLessThan(pace.get("max").asDouble());
        }

        String after = mvc.perform(get("/clubs/{c}/athletes/{a}/zone-values", clubId, athleteId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(after).isEqualTo(before);
    }
}
