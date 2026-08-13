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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La journée du coach : les séances d'un jour, tous athlètes du périmètre confondus.
 *
 * <p>Crée son propre athlète et sa propre séance, pour ne dépendre d'aucune date du jeu de démo.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachDayTest {

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
    void dayListsTheSessionsOfThatDayWithTheAthleteName() throws Exception {
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Lise\",\"lastName\":\"JourTest\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        LocalDate day = LocalDate.now().plusDays(3);
        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"" + day + "\",\"type\":\"ENDURANCE\","
                                + "\"title\":\"Footing de reprise\",\"targetDurationS\":2700}"))
                .andExpect(status().isCreated());

        JsonNode rows = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/dashboard/day", clubId)
                                .param("date", day.toString())
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode mine = null;
        for (JsonNode row : rows) {
            if (athleteId.equals(row.get("athleteId").asText())) {
                mine = row;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("title").asText()).isEqualTo("Footing de reprise");
        assertThat(mine.get("status").asText()).isEqualTo("PLANNED");
        assertThat(mine.get("athleteName").asText()).contains("JourTest");
        // Aucun retour n'a été laissé : rien n'attend d'être traité sur cette séance.
        assertThat(mine.get("awaitingReview").asBoolean()).isFalse();
    }

    @Test
    void dayIsEmptyOnADayWithoutAnySession() throws Exception {
        // Une date volontairement lointaine : le jeu de démo ne planifie rien aussi loin.
        JsonNode rows = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/dashboard/day", clubId)
                                .param("date", LocalDate.now().plusYears(3).toString())
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(rows).isEmpty();
    }

    /**
     * Le périmètre s'applique ici comme partout ailleurs sur le tableau de bord — et « mes
     * athlètes », qui est le périmètre <b>par défaut</b> de l'écran mobile, doit rendre ceux que
     * le coach suit réellement. C'est la garde qui compte : un périmètre qui se résoudrait mal
     * ne renverrait pas une liste fausse, il renverrait une journée vide, et le coach conclurait
     * qu'il n'a rien à faire aujourd'hui.
     */
    @Test
    void scopeMineKeepsTheAthletesTheCoachFollows() throws Exception {
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Tom\",\"lastName\":\"PerimetreTest\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        LocalDate day = LocalDate.now().plusDays(4);
        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"" + day + "\",\"type\":\"ENDURANCE\","
                                + "\"title\":\"Sortie longue\"}"))
                .andExpect(status().isCreated());

        JsonNode mine = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/dashboard/day", clubId)
                                .param("date", day.toString()).param("scope", "mine")
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        boolean present = false;
        for (JsonNode row : mine) {
            present |= athleteId.equals(row.get("athleteId").asText());
        }
        assertThat(present).isTrue();
    }
}
