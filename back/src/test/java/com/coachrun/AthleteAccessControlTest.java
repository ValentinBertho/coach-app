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
 * Contrôle d'accès gradué coach ↔ athlète (Chantier 1) : un coach assistant en lecture seule
 * ne peut pas écrire, et un athlète privé d'un autre coach n'est jamais accessible. S'appuie
 * sur le jeu de données démo (owner référent, assistant READ sur l'athlète club, 2 athlètes privés).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AthleteAccessControlTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String ownerBearer;     // HEAD_COACH = référent de tous les athlètes
    private String assistantBearer; // COACH_ASSISTANT = lecture seule sur l'athlète club
    private String clubId;
    private String demoAthleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        JsonNode owner = login(DemoSeedService.HEAD_COACH_EMAIL);
        ownerBearer = "Bearer " + owner.get("accessToken").asText();
        clubId = owner.get("user").get("clubId").asText();
        assistantBearer = "Bearer " + login(DemoSeedService.COACH_EMAIL).get("accessToken").asText();
        demoAthleteId = login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText();
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** Lecture seule : l'assistant lit l'athlète club mais ne peut pas déclencher une action d'écriture. */
    @Test
    void assistantCanReadButNotWrite() throws Exception {
        // READ accordé sur l'athlète club ⇒ 200.
        mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, demoAthleteId)
                        .header("Authorization", assistantBearer))
                .andExpect(status().isOk());

        // Pas de WRITE ⇒ l'invitation (action d'écriture, sans corps) est refusée.
        mvc.perform(post("/clubs/{c}/athletes/{a}/invitation", clubId, demoAthleteId)
                        .header("Authorization", assistantBearer))
                .andExpect(status().isForbidden());

        // Le référent (owner), lui, peut écrire.
        mvc.perform(post("/clubs/{c}/athletes/{a}/invitation", clubId, demoAthleteId)
                        .header("Authorization", ownerBearer))
                .andExpect(status().isOk());
    }

    /** Confidentialité : un athlète privé n'est lisible que par son référent, jamais par l'assistant. */
    @Test
    void privateAthleteIsReferentOnly() throws Exception {
        JsonNode page = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes", clubId).param("size", "50")
                                .header("Authorization", ownerBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        String privateAthleteId = null;
        for (JsonNode a : page.get("content")) {
            String id = a.get("id").asText();
            int statusCode = mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, id)
                            .header("Authorization", assistantBearer))
                    .andReturn().getResponse().getStatus();
            if (statusCode == 403) {            // inaccessible à l'assistant ⇒ athlète privé
                privateAthleteId = id;
                break;
            }
        }

        // Le jeu démo contient au moins un athlète privé hors de portée de l'assistant.
        assertThat(privateAthleteId).as("au moins un athlète privé inaccessible à l'assistant").isNotNull();

        // … mais son référent (owner) y accède sans problème.
        mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, privateAthleteId)
                        .header("Authorization", ownerBearer))
                .andExpect(status().isOk());
    }
}
