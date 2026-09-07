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
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le fil de messages est paginé, et son début reste atteignable.
 *
 * <p><b>Ce que cela répare.</b> Le fil n'était pas paginé mais <b>tronqué</b> : les cent derniers
 * messages, et rien derrière. Une conversation qui dure une saison perdait donc silencieusement
 * son début — pas de bouton, pas d'indication, un fil qui commence au milieu d'une phrase. Ni
 * l'athlète ni le coach ne pouvaient y revenir, et une demande d'accès aux données n'aurait rendu
 * que la partie visible.</p>
 *
 * <p>La taille de page est ramenée à cinq pour que le test écrive douze messages plutôt que cent
 * cinquante : ce qui est vérifié ici, c'est le découpage et l'ordre, pas le nombre.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
// Transactionnel : le fil du jeu de démonstration est partagé par toute la suite, et douze
// messages écrits par un cas se compteraient dans le suivant.
@org.springframework.transaction.annotation.Transactional
class MessageThreadPaginationTest {

    /** Taille de page demandée : assez petite pour que douze messages fassent trois pages. */
    private static final int PAGE_SIZE = 5;
    private static final int MESSAGES = 12;

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String coachBearer;
    private String athleteBearer;
    private String clubId;
    private UUID athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode coach = login(DemoSeedService.HEAD_COACH_EMAIL);
        coachBearer = "Bearer " + coach.get("accessToken").asText();
        clubId = coach.get("user").get("clubId").asText();
        JsonNode athlete = login(DemoSeedService.ATHLETE_EMAIL);
        athleteBearer = "Bearer " + athlete.get("accessToken").asText();
        athleteId = UUID.fromString(athlete.get("user").get("athleteId").asText());
    }

    /**
     * Le découpage lui-même : chaque message écrit se retrouve dans une page et une seule, et
     * parcourir toutes les pages les retrouve tous — c'est-à-dire que rien n'est perdu entre la
     * page 0 et la dernière. C'est exactement ce que le fil tronqué ne garantissait pas.
     *
     * <p>Le fil part du jeu de démonstration, qui y a déjà écrit : les assertions portent donc
     * sur ce que ce test a écrit, pas sur un total absolu.</p>
     */
    @Test
    void everyMessageIsReachableThroughSomePage() throws Exception {
        List<String> written = writeThread();

        JsonNode first = coachPage(0);
        int totalPages = first.get("totalPages").asInt();
        assertThat(first.get("totalElements").asLong())
                .as("le fil porte au moins ce que ce test y a écrit")
                .isGreaterThanOrEqualTo(MESSAGES);
        assertThat(totalPages)
                .as("douze messages de plus, cinq par page : le fil ne tient plus sur une page")
                .isGreaterThanOrEqualTo(3);

        List<String> seen = new ArrayList<>();
        for (int page = 0; page < totalPages; page++) {
            seen.addAll(ids(coachPage(page)));
        }

        assertThat(seen)
                .as("aucun message ne figure deux fois")
                .doesNotHaveDuplicates()
                .as("et aucun n'est perdu entre les pages")
                .containsAll(written);
    }

    /**
     * La page 0 porte les messages les <b>plus récents</b> : c'est là qu'on ouvre un fil. Les
     * pages suivantes remontent le temps.
     */
    @Test
    void pageZeroCarriesTheMostRecentMessages() throws Exception {
        List<String> written = writeThread();

        assertThat(ids(coachPage(0)))
                .as("la page 0 est le bas du fil : les cinq derniers écrits")
                .isEqualTo(written.subList(MESSAGES - PAGE_SIZE, MESSAGES));
        assertThat(ids(coachPage(1)))
                .as("la page 1 remonte d'une page dans le temps")
                .isEqualTo(written.subList(MESSAGES - 2 * PAGE_SIZE, MESSAGES - PAGE_SIZE));
    }

    /** À l'intérieur d'une page, l'ordre reste chronologique : c'est ainsi qu'on lit un fil. */
    @Test
    void aPageReadsInChronologicalOrder() throws Exception {
        writeThread();

        JsonNode page = coachPage(0);
        String previous = null;
        for (JsonNode m : page.get("content")) {
            String createdAt = m.get("createdAt").asText();
            if (previous != null) {
                assertThat(createdAt).isGreaterThanOrEqualTo(previous);
            }
            previous = createdAt;
        }
    }

    /** L'athlète voit le même fil, découpé de la même façon, depuis sa propre route. */
    @Test
    void theAthleteSeesTheSamePagination() throws Exception {
        writeThread();

        JsonNode page = objectMapper.readTree(mvc.perform(get("/me/messages")
                        .param("page", "0").param("size", String.valueOf(PAGE_SIZE))
                        .header("Authorization", athleteBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(MESSAGES);
        assertThat(page.get("content")).hasSize(PAGE_SIZE);
    }

    /**
     * Une taille de page libre redonnerait à qui la demande le pouvoir de charger le fil entier —
     * ce que la pagination existe précisément pour éviter.
     */
    @Test
    void theRequestedPageSizeIsCapped() throws Exception {
        writeThread();

        JsonNode page = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/messages", clubId, athleteId)
                                .param("size", "100000")
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(page.get("size").asInt()).isLessThanOrEqualTo(200);
    }

    /** Une page au-delà de la fin est vide, pas une erreur. */
    @Test
    void aPageBeyondTheEndIsEmpty() throws Exception {
        writeThread();

        assertThat(coachPage(999).get("content")).isEmpty();
    }

    // ------------------------------------------------------------------ fabrique

    /** Écrit le fil et rend les identifiants dans l'ordre chronologique d'écriture. */
    private List<String> writeThread() throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < MESSAGES; i++) {
            String body = objectMapper.writeValueAsString(
                    java.util.Map.of("body", "Message numero " + i));
            String created = mvc.perform(
                            post("/clubs/{c}/athletes/{a}/messages", clubId, athleteId)
                                    .header("Authorization", coachBearer)
                                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            ids.add(objectMapper.readTree(created).get("id").asText());
        }
        return ids;
    }

    private JsonNode coachPage(int page) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/messages", clubId, athleteId)
                                .param("page", String.valueOf(page))
                                .param("size", String.valueOf(PAGE_SIZE))
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static List<String> ids(JsonNode page) {
        List<String> out = new ArrayList<>();
        for (JsonNode m : page.get("content")) {
            out.add(m.get("id").asText());
        }
        return out;
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
