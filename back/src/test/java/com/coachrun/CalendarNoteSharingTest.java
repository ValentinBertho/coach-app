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

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La note de calendrier, désormais à deux voix.
 *
 * <p>Ce qui manquait : ni une séance, ni une indisponibilité — le fait simple qu'on signale.
 * « Piste fermée jeudi », « je finis tard mardi ». L'athlète n'avait aucun endroit où l'écrire, et
 * le coach aucun moyen de lui adresser un mot sans en faire une consigne de séance.</p>
 *
 * <p>Ce que ces tests protègent surtout : <b>le carnet du coach reste privé</b>. Les notes d'un
 * jour — « relancer sur le sommeil », « surveiller ce genou » — ont été écrites en le croyant.
 * Ouvrir l'écriture à l'athlète ne les découvre pas.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CalendarNoteSharingTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String coachBearer;
    private String athleteBearer;
    private String clubId;
    private String athleteId;

    private static final LocalDate JOUR = LocalDate.now().plusDays(3);

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode coach = login(DemoSeedService.HEAD_COACH_EMAIL);
        coachBearer = "Bearer " + coach.get("accessToken").asText();
        clubId = coach.get("user").get("clubId").asText();
        JsonNode athlete = login(DemoSeedService.ATHLETE_EMAIL);
        athleteBearer = "Bearer " + athlete.get("accessToken").asText();
        athleteId = athlete.get("user").get("athleteId").asText();
    }

    /** Le carnet du coach ne s'ouvre pas parce qu'on a ajouté une colonne. */
    @Test
    void aCoachNoteStaysPrivateUnlessShared() throws Exception {
        coachNote("Relancer sur le sommeil", false);

        assertThat(athleteNotes().toString())
                .as("une note non partagee reste le carnet de travail du coach")
                .doesNotContain("sommeil");
    }

    /** Et quand il l'adresse à l'athlète, celui-ci la lit — signée. */
    @Test
    void aSharedCoachNoteReachesTheAthlete() throws Exception {
        coachNote("Piste fermee jeudi, rendez-vous au stade", true);

        JsonNode notes = athleteNotes();
        assertThat(notes.toString()).contains("Piste fermee jeudi");
        assertThat(notes.get(0).get("authorRole").asText()).isEqualTo("COACH");
    }

    /** L'athlète pose son mot : il part partagé, sans quoi il ne servirait à rien. */
    @Test
    void anAthleteNoteIsAlwaysVisibleToTheCoach() throws Exception {
        athleteNote("Je finis tard mardi, je decale d'une heure");

        JsonNode vuParLeCoach = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/notes", clubId, athleteId)
                                .header("Authorization", coachBearer)
                                .param("from", JOUR.minusDays(1).toString())
                                .param("to", JOUR.plusDays(1).toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(vuParLeCoach.toString()).contains("Je finis tard mardi");
        assertThat(vuParLeCoach.get(0).get("authorRole").asText()).isEqualTo("ATHLETE");
        assertThat(vuParLeCoach.get(0).get("shared").asBoolean()).isTrue();
    }

    /** Il peut retirer le sien… */
    @Test
    void anAthleteCanRemoveHisOwnNote() throws Exception {
        String id = athleteNote("Erreur de saisie").get("id").asText();

        mvc.perform(delete("/me/calendar-notes/{id}", id).header("Authorization", athleteBearer))
                .andExpect(status().isNoContent());

        assertThat(athleteNotes().toString()).doesNotContain("Erreur de saisie");
    }

    /** …et seulement le sien : effacer le mot de son coach n'est pas la même chose. */
    @Test
    void anAthleteCannotRemoveTheCoachNote() throws Exception {
        String id = coachNote("Objectif de la semaine", true).get("id").asText();

        mvc.perform(delete("/me/calendar-notes/{id}", id).header("Authorization", athleteBearer))
                .andExpect(status().isNotFound());

        assertThat(athleteNotes().toString()).contains("Objectif de la semaine");
    }

    // --- Utilitaires ---------------------------------------------------------------------------

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode coachNote(String texte, boolean partage) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/notes", clubId, athleteId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "noteDate", JOUR.toString(), "text", texte, "shared", partage))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode athleteNote(String texte) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/me/calendar-notes")
                        .header("Authorization", athleteBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "noteDate", JOUR.toString(), "text", texte))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode athleteNotes() throws Exception {
        return objectMapper.readTree(mvc.perform(get("/me/calendar-notes")
                        .header("Authorization", athleteBearer)
                        .param("from", JOUR.minusDays(1).toString())
                        .param("to", JOUR.plusDays(1).toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
