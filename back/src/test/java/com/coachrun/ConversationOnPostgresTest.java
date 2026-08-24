package com.coachrun;

import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La messagerie, sur un <b>vrai</b> PostgreSQL.
 *
 * <p><b>Pourquoi ce test existe.</b> La suite tourne sur H2 en mode PostgreSQL, ce qui suffit
 * presque toujours — mais pas ici : H2 accepte un paramètre nul dont il ne connaît pas le type, là
 * où PostgreSQL refuse la requête. Le compte des non-lus, écrit avec un {@code :since} nul au
 * premier passage dans un fil, était donc vert en test et renvoyait 500 en production. L'écran
 * « Messages » affichait « Chargement des conversations impossible » à tout le monde.</p>
 *
 * <p>Ignoré sauf si {@code -Dpgtest=true} est passé (et qu'un PostgreSQL écoute) : la suite
 * ordinaire ne doit pas dépendre d'un serveur externe.</p>
 */
@SpringBootTest
@ActiveProfiles("pgtest")
@EnabledIfSystemProperty(named = "pgtest", matches = "true")
class ConversationOnPostgresTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** L'écran d'un athlète : la boîte de réception, non lus compris. */
    @Test
    void theAthleteInboxLoads() throws Exception {
        mvc.perform(get("/me/conversations").header("Authorization", bearer(DemoSeedService.ATHLETE_EMAIL)))
                .andExpect(status().isOk());
    }

    /** Et celle d'un coach, qui compte en plus ses fils de groupe et de club. */
    @Test
    void theCoachInboxLoads() throws Exception {
        String bearer = bearer(DemoSeedService.HEAD_COACH_EMAIL);
        mvc.perform(get("/me/conversations").header("Authorization", bearer))
                .andExpect(status().isOk());
        mvc.perform(get("/me/conversations/unread-count").header("Authorization", bearer))
                .andExpect(status().isOk());
    }

    private String bearer(String email) throws Exception {
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return "Bearer " + auth.get("accessToken").asText();
    }
}
