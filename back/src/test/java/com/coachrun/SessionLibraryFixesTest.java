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
 * Correctifs de la bibliothèque de séances remontés par les coachs :
 * identité éditable depuis l'éditeur, catégorie qui survit à l'auto-sauvegarde, titre seul exigé
 * à la création, versement d'une séance du calendrier dans la bibliothèque, et bibliothèque
 * complète (au-delà des vingt premiers modèles).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SessionLibraryFixesTest {

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

        JsonNode auth = json(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoSeedService.HEAD_COACH_EMAIL
                                + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        bearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();

        JsonNode athletes = json(mvc.perform(get("/clubs/{c}/athletes?size=50", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        athleteId = athletes.get("content").get(0).get("id").asText();
    }

    /** Le titre suffit : le nom de rangement reprend le titre quand il n'est pas fourni. */
    @Test
    void templateCreatedWithTitleOnly() throws Exception {
        JsonNode created = json(mvc.perform(post("/clubs/{c}/workout-templates", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"INTERVALS\",\"title\":\"VMA 10x400 m\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(created.get("name").asText()).isEqualTo("VMA 10x400 m");
    }

    /**
     * L'auto-sauvegarde de l'éditeur n'envoie que la structure : la catégorie choisie à la
     * création doit survivre. Le renommage, lui, passe par le même appel.
     */
    @Test
    void structureSaveKeepsCategoryAndRenames() throws Exception {
        String categoryId = json(mvc.perform(post("/clubs/{c}/session-categories", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Seuil\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        String templateId = json(mvc.perform(post("/clubs/{c}/workout-templates", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENDURANCE\",\"title\":\"Seuil 20 min\",\"categoryId\":\""
                                + categoryId + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // Auto-sauvegarde « structure seule » : la catégorie reste en place.
        JsonNode autosaved = json(mvc.perform(put("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"structure\":{\"warmup\":[],\"main\":[],\"cooldown\":[]}}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(autosaved.get("categoryId").asText()).isEqualTo(categoryId);

        // Renommage depuis l'éditeur (le geste impossible après une duplication).
        JsonNode renamed = json(mvc.perform(put("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Seuil long\",\"title\":\"3x10 min seuil\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(renamed.get("name").asText()).isEqualTo("Seuil long");
        assertThat(renamed.get("title").asText()).isEqualTo("3x10 min seuil");
        assertThat(renamed.get("categoryId").asText()).isEqualTo(categoryId);

        // Détachement explicite.
        JsonNode detached = json(mvc.perform(put("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearCategory\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(detached.get("categoryId").isNull()).isTrue();
    }

    /** Une séance construite au calendrier se verse dans la bibliothèque avec sa structure. */
    @Test
    void workoutSavedAsTemplate() throws Exception {
        String workoutId = json(mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"2026-09-01\",\"type\":\"ENDURANCE\","
                                + "\"title\":\"Séance improvisée\",\"steps\":[]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        mvc.perform(put("/clubs/{c}/athletes/{a}/workouts/{w}/structure", clubId, athleteId, workoutId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warmup\":[],\"main\":[{\"id\":\"b1\",\"type\":\"intervals\","
                                + "\"reps\":6,\"distanceM\":1000}],\"cooldown\":[]}"))
                .andExpect(status().isOk());

        JsonNode template = json(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts/{w}/save-as-template", clubId, athleteId, workoutId)
                                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"6x1000 m\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(template.get("name").asText()).isEqualTo("6x1000 m");
        assertThat(template.get("structure").get("main")).hasSize(1);

        // Le modèle est bien dans la bibliothèque du club.
        JsonNode library = json(mvc.perform(get("/clubs/{c}/workout-templates?size=200", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(library.get("content").toString()).contains("6x1000 m");
    }

    /** La bibliothèque n'est plus plafonnée à vingt modèles pour un écran sans pagination. */
    @Test
    void libraryReturnsMoreThanTwentyTemplates() throws Exception {
        for (int i = 0; i < 25; i++) {
            mvc.perform(post("/clubs/{c}/workout-templates", clubId)
                            .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"ENDURANCE\",\"title\":\"Séance " + i + "\"}"))
                    .andExpect(status().isCreated());
        }
        JsonNode page = json(mvc.perform(get("/clubs/{c}/workout-templates", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(page.get("content").size()).isGreaterThan(20);
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
