package com.coachrun;

import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Vérifie le modèle ouvert (many-to-many) : un athlète peut avoir plusieurs coachs et
 * plusieurs clubs, un plan peut être attribué à plusieurs athlètes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ManyToManyRelationsTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DemoSeedService demoSeedService;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void athleteHasMultipleCoachesAndClubsAndPlans() throws Exception {
        demoSeedService.seed();
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoSeedService.HEAD_COACH_EMAIL
                                + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String bearer = "Bearer " + auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();

        // Le seed rattache plusieurs coachs à au moins un athlète et l'inscrit à un club additionnel.
        JsonNode athletes = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes?size=50", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        // L'ordre de la liste (tri par nom) départage mal les homonymes et diffère entre H2 et
        // PostgreSQL : on cherche l'athlète suivi plutôt que de supposer qu'il est le premier.
        String athleteId = null;
        JsonNode detail = null;
        for (JsonNode a : athletes.get("content")) {
            JsonNode d = objectMapper.readTree(mvc.perform(
                            get("/clubs/{c}/athletes/{a}", clubId, a.get("id").asText()).header("Authorization", bearer))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            if (d.get("coaches") != null && !d.get("coaches").isEmpty()) {
                athleteId = a.get("id").asText();
                detail = d;
                break;
            }
        }
        assertThat(athleteId).as("au moins un athlète du seed a un coach").isNotNull();
        assertThat(detail.get("coaches")).isNotEmpty();          // plusieurs coachs possibles
        assertThat(detail.get("clubs").size()).isGreaterThanOrEqualTo(1);

        // Coachs assignables du club.
        JsonNode coaches = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/assignable-coaches", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(coaches).isNotEmpty();
        String coachId = coaches.get(0).get("id").asText();

        // Assigner puis retirer un coach (idempotent, jamais d'erreur).
        mvc.perform(put("/clubs/{c}/athletes/{a}/coaches/{co}", clubId, athleteId, coachId)
                .header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(delete("/clubs/{c}/athletes/{a}/coaches/{co}", clubId, athleteId, coachId)
                .header("Authorization", bearer)).andExpect(status().isOk());

        // Les plans attribués à un athlète sont exposés.
        mvc.perform(get("/clubs/{c}/athletes/{a}/plans", clubId, athleteId)
                .header("Authorization", bearer)).andExpect(status().isOk());

        // Un plan de démo est attribué à plusieurs athlètes.
        JsonNode plans = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/training-plans", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(plans).isNotEmpty();
        assertThat(plans.get(0).get("athletes")).isNotNull();
    }
}
