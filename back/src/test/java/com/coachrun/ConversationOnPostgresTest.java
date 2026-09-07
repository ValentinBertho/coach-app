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

    /**
     * Le geste de « Nouveau message », sur le moteur de production : ouvrir un fil qui n'existe
     * pas encore, puis le lire.
     */
    @Test
    void openingABrandNewConversationWorks() throws Exception {
        String athleteBearer = bearer(DemoSeedService.ATHLETE_EMAIL);
        JsonNode recipients = objectMapper.readTree(
                mvc.perform(get("/me/conversations/recipients").header("Authorization", athleteBearer))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(recipients.size())
                .as("l'athlete de demonstration a au moins un coach a qui ecrire")
                .isGreaterThan(0);
        JsonNode target = recipients.get(0);

        String body = mvc.perform(post("/me/conversations/open")
                        .header("Authorization", athleteBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "kind", target.get("kind").asText(),
                                "targetId", target.get("id").asText()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        mvc.perform(get("/me/conversations/{id}/messages", objectMapper.readTree(body).get("id").asText())
                        .header("Authorization", athleteBearer))
                .andExpect(status().isOk());
    }

    /**
     * La pagination du fil, sur le moteur de production.
     *
     * <p>Une page de fil, c'est deux requêtes SQL : la page triée et son décompte. Elles sont
     * générées par Spring Data et non écrites à la main, mais c'est exactement le genre de code
     * qui n'a jamais été exécuté ailleurs qu'en H2 — et la messagerie est déjà partie en 500 en
     * production pour une requête que H2 acceptait. Ici, le fil rendu par la page 1 ne servirait
     * plus la moitié ancienne de la conversation.</p>
     */
    @Test
    void aPaginatedThreadLoads() throws Exception {
        String bearer = bearer(DemoSeedService.HEAD_COACH_EMAIL);
        JsonNode inbox = objectMapper.readTree(
                mvc.perform(get("/me/conversations").header("Authorization", bearer))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(inbox.size())
                .as("le jeu de demonstration ouvre au moins un fil au coach")
                .isGreaterThan(0);
        String conversationId = inbox.get(0).get("id").asText();

        for (String page : new String[] {"0", "1"}) {
            String body = mvc.perform(get("/me/conversations/{id}/messages", conversationId)
                            .param("page", page).param("size", "5")
                            .header("Authorization", bearer))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(body).has("totalPages"))
                    .as("la page %s porte bien une enveloppe de pagination", page)
                    .isTrue();
        }
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
