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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Résidu QA1 — arbre de catégories unifié (course · prépa physique · éducatifs) discriminé par
 * domaine, et rattachement (categoryId) des exercices / éducatifs à cet arbre.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UnifiedCategoryTest {

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
    void categoriesAreScopedByDomain() throws Exception {
        // Catégorie course (domaine par défaut) et catégorie prépa physique.
        mvc.perform(post("/clubs/{c}/session-categories", clubId).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Vitesse\"}"))
                .andExpect(status().isCreated());
        JsonNode strengthCat = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/session-categories?domain=STRENGTH", clubId).header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Gainage\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(strengthCat.get("domain").asText()).isEqualTo("STRENGTH");

        // Chaque domaine ne voit que ses catégories.
        JsonNode course = objectMapper.readTree(mvc.perform(get("/clubs/{c}/session-categories", clubId)
                        .header("Authorization", bearer)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode strength = objectMapper.readTree(mvc.perform(get("/clubs/{c}/session-categories?domain=STRENGTH", clubId)
                        .header("Authorization", bearer)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        // Le club est seedé avec des catégories de course : on vérifie le cloisonnement par
        // domaine (la catégorie course est visible côté course et absente côté force, et
        // réciproquement), pas un total absolu.
        List<String> courseNames = new ArrayList<>();
        course.forEach(c -> courseNames.add(c.get("name").asText()));
        List<String> strengthNames = new ArrayList<>();
        strength.forEach(c -> strengthNames.add(c.get("name").asText()));
        assertThat(courseNames).contains("Vitesse").doesNotContain("Gainage");
        assertThat(strengthNames).containsExactly("Gainage");
    }

    @Test
    void drillCanBeAttachedToDrillCategory() throws Exception {
        JsonNode cat = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/session-categories?domain=DRILL", clubId).header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pieds\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String catId = cat.get("id").asText();

        JsonNode drill = objectMapper.readTree(mvc.perform(post("/clubs/{c}/run-drills", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Montées de genoux\",\"category\":\"TECHNIQUE\",\"categoryId\":\"" + catId + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(drill.get("categoryId").asText()).isEqualTo(catId);
    }

    @Test
    void exerciseRejectsCategoryFromWrongDomain() throws Exception {
        // Catégorie créée dans le domaine DRILL…
        JsonNode cat = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/session-categories?domain=DRILL", clubId).header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pieds\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String drillCatId = cat.get("id").asText();

        // …refusée pour un exercice de prépa physique (domaine STRENGTH attendu).
        mvc.perform(post("/clubs/{c}/pp/exercises", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Planche\",\"category\":\"GAINAGE\",\"categoryId\":\"" + drillCatId + "\"}"))
                .andExpect(status().isNotFound());
    }
}
