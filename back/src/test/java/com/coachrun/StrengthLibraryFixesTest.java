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
 * Les correctifs de la bibliothèque course s'arrêtaient à la course : la préparation physique
 * gardait le plafond de vingt items et n'offrait aucun renommage après duplication. Même
 * couverture, côté force — plus l'étanchéité des arbres de catégories entre bibliothèques.
 *
 * <p>Les libellés envoyés restent en ASCII : MockMvc encode le corps de requête et relit la
 * réponse en ISO-8859-1, ce qui ferait échouer une comparaison d'accents sans que l'application
 * y soit pour quoi que ce soit.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StrengthLibraryFixesTest {

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

        JsonNode auth = json(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoSeedService.HEAD_COACH_EMAIL
                                + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        bearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();
    }

    /** Une séance de force dupliquée doit pouvoir être renommée (elle restait « … (copie) »). */
    @Test
    void strengthSessionCanBeRenamedAfterDuplicate() throws Exception {
        String sessionId = json(mvc.perform(post("/clubs/{c}/pp/sessions", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Force max\",\"notes\":\"Bas du corps\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        JsonNode copy = json(mvc.perform(post("/clubs/{c}/pp/sessions/{s}/duplicate", clubId, sessionId)
                        .header("Authorization", bearer))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(copy.get("name").asText()).isEqualTo("Force max (copie)");

        JsonNode renamed = json(mvc.perform(put("/clubs/{c}/pp/sessions/{s}", clubId, copy.get("id").asText())
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Force max - variante\",\"notes\":\"Bas du corps\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(renamed.get("name").asText()).isEqualTo("Force max - variante");
        // Les notes ne doivent pas disparaître au passage : l'écran les renvoie telles quelles.
        assertThat(renamed.get("notes").asText()).isEqualTo("Bas du corps");
    }

    /** La bibliothèque de force n'est plus plafonnée à vingt séances. */
    @Test
    void strengthLibraryReturnsMoreThanTwentySessions() throws Exception {
        for (int i = 0; i < 25; i++) {
            mvc.perform(post("/clubs/{c}/pp/sessions", clubId)
                            .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Seance force " + i + "\"}"))
                    .andExpect(status().isCreated());
        }
        JsonNode page = json(mvc.perform(get("/clubs/{c}/pp/sessions", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(page.get("content").size()).isGreaterThan(20);
    }

    /** Idem pour le catalogue d'exercices, qui alimente le sélecteur de l'éditeur. */
    @Test
    void exerciseCatalogReturnsMoreThanTwentyItems() throws Exception {
        for (int i = 0; i < 25; i++) {
            mvc.perform(post("/clubs/{c}/pp/exercises", clubId)
                            .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Exercice " + i + "\",\"category\":\"GAINAGE\"}"))
                    .andExpect(status().isCreated());
        }
        JsonNode page = json(mvc.perform(get("/clubs/{c}/pp/exercises", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(page.get("content").size()).isGreaterThan(20);
    }

    /**
     * Sous-catégories : l'arbre reste dans sa bibliothèque. Rangée sous une catégorie de force,
     * une catégorie course sortirait des deux listes, qui filtrent par domaine.
     */
    @Test
    void categoryCannotBeParentedAcrossLibraries() throws Exception {
        String strengthParent = json(mvc.perform(post("/clubs/{c}/session-categories?domain=STRENGTH", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Haut du corps\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        String courseCategory = json(mvc.perform(post("/clubs/{c}/session-categories", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VMA\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        mvc.perform(put("/clubs/{c}/session-categories/{id}", clubId, courseCategory)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VMA\",\"parentId\":\"" + strengthParent + "\"}"))
                .andExpect(status().isConflict());

        // Une sous-catégorie du même domaine, elle, passe.
        JsonNode child = json(mvc.perform(post("/clubs/{c}/session-categories", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VMA courte\",\"parentId\":\"" + courseCategory + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(child.get("parentId").asText()).isEqualTo(courseCategory);
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
