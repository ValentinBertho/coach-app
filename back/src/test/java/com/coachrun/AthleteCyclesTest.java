package com.coachrun;

import com.coachrun.entity.User;
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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les cycles d'entraînement vus par l'athlète.
 *
 * <p>Le coach pose des périodes sur le calendrier — « bloc spécifique », « affûtage ». Il les
 * voit depuis toujours ; l'athlète recevait ses séances sans jamais savoir dans quelle phase
 * elles s'inscrivaient. La même donnée porte pourtant deux choses très différentes : une note de
 * <b>période</b> décrit l'entraînement et regarde l'athlète, une note d'un <b>seul jour</b> est
 * le pense-bête du coach. Ces tests fixent cette frontière — c'est elle qui rend l'ouverture
 * sûre.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AthleteCyclesTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private com.coachrun.repository.UserRepository userRepository;

    private MockMvc mvc;
    private String coach;
    private String athlete;
    private String clubId;
    private String athleteId;

    private static final LocalDate START = LocalDate.of(2026, 8, 3);
    private static final LocalDate END = LocalDate.of(2026, 8, 30);

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        coach = bearer(DemoSeedService.HEAD_COACH_EMAIL);
        athlete = bearer(DemoSeedService.ATHLETE_EMAIL);

        User athleteUser = userRepository.findByEmailIgnoreCase(DemoSeedService.ATHLETE_EMAIL).orElseThrow();
        athleteId = athleteUser.getAthlete().getId().toString();
        clubId = athleteUser.getClub().getId().toString();
    }

    private String bearer(String email) throws Exception {
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return "Bearer " + auth.get("accessToken").asText();
    }

    /** Le coach pose une note ; sans date de fin, c'est une note d'un seul jour. */
    private void coachNote(String text, LocalDate date, LocalDate end) throws Exception {
        String body = "{\"noteDate\":\"" + date + "\",\"text\":\"" + text + "\""
                + (end == null ? "" : ",\"endDate\":\"" + end + "\"") + "}";
        mvc.perform(post("/clubs/{c}/athletes/{a}/notes", clubId, athleteId)
                        .header("Authorization", coach)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    /**
     * Le besoin même : l'athlète lit la phase dans laquelle il court, avec ses bornes — c'est ce
     * qui distingue trois semaines de côtes d'un bloc dont on voit la fin.
     */
    @Test
    void lAthleteVoitLesCyclesPosesParSonCoach() throws Exception {
        coachNote("Bloc spécifique", START, END);

        mvc.perform(get("/me/cycles")
                        .param("from", START.toString()).param("to", END.toString())
                        .header("Authorization", athlete))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Bloc spécifique"))
                .andExpect(jsonPath("$[0].noteDate").value(START.toString()))
                .andExpect(jsonPath("$[0].endDate").value(END.toString()));
    }

    /**
     * La garde qui compte. Sur le même calendrier, le coach écrit aussi « relancer le kiné » ou
     * « ne croit pas à son objectif » : ces notes d'un jour sont son espace de travail, elles ne
     * sont pas adressées à l'athlète et ne doivent jamais franchir la frontière.
     */
    @Test
    void lesNotesDUnSeulJourRestentChezLeCoach() throws Exception {
        coachNote("Bloc spécifique", START, END);
        coachNote("appeler le kine", START.plusDays(2), null);
        // Une « période » d'un seul jour n'en est pas une : même traitement.
        coachNote("point telephonique", START.plusDays(3), START.plusDays(3));

        mvc.perform(get("/me/cycles")
                        .param("from", START.toString()).param("to", END.toString())
                        .header("Authorization", athlete))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Bloc spécifique"));
    }

    /** Hors de la plage demandée, rien ne remonte : l'agenda ne charge que ce qu'il affiche. */
    @Test
    void seulsLesCyclesDeLaPlageRemontent() throws Exception {
        coachNote("Bloc spécifique", START, END);

        mvc.perform(get("/me/cycles")
                        .param("from", END.plusDays(1).toString())
                        .param("to", END.plusMonths(1).toString())
                        .header("Authorization", athlete))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** Route du portail athlète : elle n'existe pas plus que les autres sans jeton. */
    @Test
    void unAnonymeNObtientRien() throws Exception {
        mvc.perform(get("/me/cycles")
                        .param("from", START.toString()).param("to", END.toString()))
                .andExpect(status().isUnauthorized());
    }
}
