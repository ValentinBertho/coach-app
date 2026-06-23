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
 * Préparation physique : bibliothèque d'exercices (CRUD + filtres) et calcul du e1RM (Nuzzo).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PpExerciseControllerTest {

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
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        bearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();
    }

    @Test
    void createsAndFiltersExercises() throws Exception {
        // Noms et groupes musculaires distinctifs (le jeu de démo seede déjà quelques exercices).
        mvc.perform(post("/clubs/{c}/pp/exercises", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Mollet TestUnit","category":"FORCE_MAX","level":"AVANCE",
                             "muscleGroups":["MOLLETS"],"equipment":["BARRE"],
                             "videoUrl":"https://youtu.be/demo","instructions":"Dos gainé"}"""))
                .andExpect(status().isCreated());
        mvc.perform(post("/clubs/{c}/pp/exercises", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Hanche TestUnit","category":"MOBILITE",
                             "muscleGroups":["HANCHE"],"equipment":["ELASTIQUE"]}"""))
                .andExpect(status().isCreated());

        // Filtre par groupe musculaire (member of) — MOLLETS absent du jeu de démo.
        JsonNode mollets = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/pp/exercises?muscle=MOLLETS", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(mollets.get("content")).hasSize(1);
        assertThat(mollets.get("content").get(0).get("name").asText()).isEqualTo("Mollet TestUnit");

        JsonNode hanche = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/pp/exercises?muscle=HANCHE", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(hanche.get("content")).hasSize(1);
        assertThat(hanche.get("content").get(0).get("name").asText()).isEqualTo("Hanche TestUnit");

        // Recherche texte sur le suffixe unique.
        JsonNode all = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/pp/exercises?q=TestUnit", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(all.get("content")).hasSize(2);
    }

    @Test
    void computesE1rmWithNuzzo() throws Exception {
        JsonNode r = objectMapper.readTree(mvc.perform(post("/clubs/{c}/pp/calc/e1rm", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weight\":15,\"reps\":6}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(r.get("formula").asText()).isEqualTo("NUZZO");
        assertThat(r.get("e1rm").asDouble()).isCloseTo(16.76, org.assertj.core.data.Offset.offset(0.05));
        assertThat(r.get("zones")).hasSize(4);
    }
}
