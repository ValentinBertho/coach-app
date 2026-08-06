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
 * Cycles du calendrier : une note du coach posée sur une <b>période</b>.
 *
 * <p>Une note ne tenait que sur un jour, alors que ce qu'un coach inscrit sur son calendrier est
 * le plus souvent un bloc de plusieurs semaines — « spécifique », « affûtage ». Deux règles en
 * découlent, et c'est la première qui rendait la fonction inutilisable sans elle : un cycle
 * commencé avant le mois affiché doit rester visible sur ce mois, sans quoi il disparaît
 * précisément des semaines qu'il occupe.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CalendarCycleTest {

    // Libellés sans accent : MockMvc décode la réponse avec un jeu de caractères qui les abîme,
    // et ce n'est pas l'encodage qui est testé ici.

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

        JsonNode athletes = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes?size=50", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        athleteId = athletes.get("content").get(0).get("id").asText();
    }

    private JsonNode createNote(String body) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/notes", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode listNotes(String from, String to) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/notes?from={f}&to={t}", clubId, athleteId, from, to)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void aNoteCanCoverAPeriod() throws Exception {
        JsonNode note = createNote(
                "{\"noteDate\":\"2026-09-07\",\"endDate\":\"2026-10-04\",\"text\":\"Bloc specifique\"}");

        assertThat(note.get("endDate").asText()).isEqualTo("2026-10-04");
        assertThat(note.get("text").asText()).isEqualTo("Bloc specifique");
    }

    /**
     * Le cas qui rend la fonction utilisable : la fenêtre lue est un mois, le cycle commence
     * avant. Avec une recherche sur la seule date de début, il n'apparaîtrait sur aucun des mois
     * qu'il traverse.
     */
    @Test
    void aCycleStartedBeforeTheWindowIsStillListed() throws Exception {
        createNote("{\"noteDate\":\"2026-09-07\",\"endDate\":\"2026-10-04\",\"text\":\"Bloc specifique\"}");

        JsonNode october = listNotes("2026-10-01", "2026-10-31");

        assertThat(october).hasSize(1);
        assertThat(october.get(0).get("text").asText()).isEqualTo("Bloc specifique");
    }

    /** Hors de la période, rien : un cycle clos en octobre n'a rien à dire de novembre. */
    @Test
    void aCycleIsAbsentFromWindowsItDoesNotTouch() throws Exception {
        createNote("{\"noteDate\":\"2026-09-07\",\"endDate\":\"2026-10-04\",\"text\":\"Bloc specifique\"}");

        assertThat(listNotes("2026-11-01", "2026-11-30")).isEmpty();
    }

    /** Une note d'un jour reste une note d'un jour — l'écriture existante ne bouge pas. */
    @Test
    void aNoteWithoutEndDateStaysOnItsDay() throws Exception {
        JsonNode note = createNote("{\"noteDate\":\"2026-09-07\",\"text\":\"Test terrain\"}");

        assertThat(note.get("endDate").isNull()).isTrue();
        assertThat(listNotes("2026-09-01", "2026-09-30")).hasSize(1);
        assertThat(listNotes("2026-09-08", "2026-09-30")).isEmpty();
    }

    /** Une fin égale au début ne décrit pas une période : la note redevient celle d'un jour. */
    @Test
    void anEndEqualToTheStartIsNotAPeriod() throws Exception {
        JsonNode note = createNote(
                "{\"noteDate\":\"2026-09-07\",\"endDate\":\"2026-09-07\",\"text\":\"Test terrain\"}");

        assertThat(note.get("endDate").isNull()).isTrue();
    }

    /** Une fin antérieure au début est refusée, pas corrigée en silence. */
    @Test
    void anEndBeforeTheStartIsRefused() throws Exception {
        mvc.perform(post("/clubs/{c}/athletes/{a}/notes", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noteDate\":\"2026-09-07\",\"endDate\":\"2026-09-01\",\"text\":\"Bloc\"}"))
                .andExpect(status().isBadRequest());
    }
}
