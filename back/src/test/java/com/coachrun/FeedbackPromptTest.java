package com.coachrun;

import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auto-détection : quand une activité importée est rapprochée d'une séance, la feuille de
 * ressenti s'ouvre pré-remplie à la prochaine ouverture du portail.
 *
 * <p>La fuite que ça bouche : le rapprochement passe la séance en COMPLETED, donc elle sort du
 * bandeau « retours en attente » — sans que l'athlète ait jamais déclaré fatigue ni douleur. Le
 * coach n'aurait aucun signal de forme sur toutes les séances venues de la montre.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeedbackPromptTest {

    /** Garde-fou de la boucle de purge : le jeu de démo n'a jamais autant d'activités. */
    private static final int MAX_DRAIN = 50;

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String athleteBearer;
    private String coachBearer;
    private String clubId;
    private String athleteId;

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

        drainPrompts();
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** Le jeu de démo contient déjà des rapprochements : on part d'une file vide. */
    private void drainPrompts() throws Exception {
        for (int i = 0; i < MAX_DRAIN; i++) {
            JsonNode prompt = prompt();
            if (prompt == null) {
                return;
            }
            ack(prompt.get("activityId").asText());
        }
        throw new AssertionError("File d'invitations au ressenti jamais vidée");
    }

    /** Invitation courante, ou {@code null} sur 204. */
    private JsonNode prompt() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/me/feedback-prompt")
                .header("Authorization", athleteBearer)).andReturn().getResponse();
        assertThat(response.getStatus()).isIn(200, 204);
        return response.getStatus() == 204 ? null : objectMapper.readTree(response.getContentAsString());
    }

    private void ack(String activityId) throws Exception {
        mvc.perform(post("/me/feedback-prompt/{a}/ack", activityId).header("Authorization", athleteBearer))
                .andExpect(status().isNoContent());
    }

    /** Séance de 10 km rapprochée d'une activité de 10,2 km le même jour. */
    private String seedMatchedActivity(LocalDate day, String externalId) throws Exception {
        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"" + day
                                + "\",\"type\":\"ENDURANCE\",\"title\":\"Sortie montre\",\"targetDistanceM\":10000}"))
                .andExpect(status().isCreated());

        JsonNode activity = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"source\":\"STRAVA\",\"externalId\":\"" + externalId
                                        + "\",\"activityDate\":\"" + day
                                        + "\",\"distanceM\":10200,\"durationS\":2700,\"avgHr\":148}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(activity.get("status").asText()).isEqualTo("MATCHED");
        return activity.get("matchedWorkoutId").asText();
    }

    @Test
    void matchedActivityIsProposedWithItsMeasuredFacts() throws Exception {
        String workoutId = seedMatchedActivity(LocalDate.now(), "prompt-1");

        JsonNode prompt = prompt();
        assertThat(prompt).isNotNull();
        assertThat(prompt.get("workout").get("id").asText()).isEqualTo(workoutId);
        // Les faits mesurés sont là : l'athlète confirme, il ne les ressaisit pas.
        assertThat(prompt.get("distanceM").asInt()).isEqualTo(10200);
        assertThat(prompt.get("durationS").asInt()).isEqualTo(2700);
        assertThat(prompt.get("avgHr").asInt()).isEqualTo(148);
        assertThat(prompt.get("source").asText()).isEqualTo("STRAVA");
        // …et le ressenti, lui, est bien vide : c'est tout l'intérêt de la relance.
        assertThat(prompt.get("workout").get("rpe").isNull()).isTrue();
    }

    @Test
    void promptIsProposedOnlyOnce() throws Exception {
        seedMatchedActivity(LocalDate.now(), "prompt-2");

        JsonNode first = prompt();
        assertThat(first).isNotNull();
        ack(first.get("activityId").asText());

        // Écartée = plus jamais reproposée : une feuille refusée qui revient à chaque
        // lancement n'est plus un rappel, c'est une pop-up.
        assertThat(prompt()).isNull();
    }

    @Test
    void athleteWhoAlreadySpokeIsNotAskedAgain() throws Exception {
        String workoutId = seedMatchedActivity(LocalDate.now(), "prompt-3");

        mvc.perform(patch("/me/workouts/{w}/feedback", workoutId)
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"rpe\":6,\"fatigue\":4,\"pain\":0}"))
                .andExpect(status().isOk());

        assertThat(prompt()).isNull();
    }

    // --- Sorties hors programme ---------------------------------------------

    /** Sortie libre de l'athlète, un jour sans séance prescrite : rien ne peut la rapprocher. */
    private String seedUnplannedRun(LocalDate day, String title) throws Exception {
        JsonNode activity = objectMapper.readTree(mvc.perform(post("/me/activities")
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityDate\":\"" + day + "\",\"title\":\"" + title
                                + "\",\"distanceM\":21100,\"durationS\":6300}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(activity.get("status").asText()).isEqualTo("UNMATCHED");
        return activity.get("id").asText();
    }

    /**
     * Le cas que la relance ignorait : une course, un footing improvisé. Aucune prescription à
     * comparer, donc rien d'autre à lire pour le coach que le ressenti — et c'était précisément
     * le seul cas où on ne le demandait jamais, l'athlète devant penser à aller le saisir sur sa
     * sortie.
     */
    @Test
    void unplannedRunIsProposedToo() throws Exception {
        // Un jour libre du jeu de démo : le rapprochement automatique ne doit rien trouver.
        String activityId = seedUnplannedRun(LocalDate.now().plusDays(3), "Semi du dimanche");

        JsonNode prompt = prompt();
        assertThat(prompt).isNotNull();
        assertThat(prompt.get("activityId").asText()).isEqualTo(activityId);
        // Pas de séance : le ressenti se posera sur la sortie elle-même.
        assertThat(prompt.get("workout").isNull()).isTrue();
        // Le titre et la date remplacent la séance comme repère à l'écran.
        assertThat(prompt.get("title").asText()).isEqualTo("Semi du dimanche");
        assertThat(prompt.get("activityDate").asText()).isEqualTo(LocalDate.now().plusDays(3).toString());
        assertThat(prompt.get("distanceM").asInt()).isEqualTo(21100);
    }

    /** Ressenti déjà posé sur la sortie = l'athlète s'est exprimé, on ne le relance pas. */
    @Test
    void unplannedRunAlreadyRatedIsNotProposed() throws Exception {
        String activityId = seedUnplannedRun(LocalDate.now().plusDays(4), "Footing du soir");

        mvc.perform(patch("/me/activities/{a}", activityId)
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rpe\":7}"))
                .andExpect(status().isOk());

        assertThat(prompt()).isNull();
    }
}
