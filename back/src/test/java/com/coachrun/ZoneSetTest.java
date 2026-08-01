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

        assertThat(sets).hasSize(1);
        assertThat(sets.get(0).get("isDefault").asBoolean()).isTrue();
        assertThat(sets.get(0).get("zoneCount").asInt()).isEqualTo(zones.size());
        assertThat(zones.size()).isGreaterThan(0);
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
}
