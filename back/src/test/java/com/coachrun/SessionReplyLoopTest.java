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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'autre moitié de la boucle : <b>la réponse revient jusqu'au coach</b>.
 *
 * <p><b>Ce que ces tests protègent.</b> {@link CoachCommentLoopTest} vérifie que le mot du coach
 * arrive et que l'athlète peut y répondre. La suite s'arrêtait là : la réponse partait dans le
 * fil de messagerie et n'apparaissait <b>nulle part ailleurs</b> — ni sur la séance commentée,
 * ni sur le cockpit, ni dans le digest du matin. Sa notification s'annonçait « Nouveau message »
 * et menait à la boîte de réception, où il fallait encore deviner de quelle sortie on parlait ;
 * pire, l'anti-rafale de la messagerie pouvait l'avaler derrière n'importe quel autre message du
 * même fil. En bêta, une réponse envoyée le soir même a été découverte trois jours plus tard, et
 * la conversation a fini sur WhatsApp.</p>
 *
 * <p>Chaque maillon est vérifié séparément : la panne était silencieuse de bout en bout, rien
 * n'échouait, l'échange s'arrêtait simplement.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SessionReplyLoopTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String coachBearer;
    private String clubId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode auth = login(DemoSeedService.HEAD_COACH_EMAIL);
        coachBearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();
    }

    // --- 1. La réponse laisse une trace SUR LA SÉANCE ----------------------------------------

    @Test
    void anAthleteReplyMarksTheSessionAsAwaitingTheCoach() throws Exception {
        String workoutId = commentedWorkout("Tu te sentais facile sur les allures ?");
        reply(workoutId, "Oui, très facile !");

        JsonNode workout = coachWorkout(workoutId);
        assertThat(workout.hasNonNull("athleteReplyAt"))
                .as("la séance sait qu'on y a répondu").isTrue();
        assertThat(workout.hasNonNull("athleteReplyReadAt"))
                .as("et que personne ne l'a encore lu").isFalse();
    }

    /** Sans réponse, rien n'attend : une séance simplement commentée ne doit pas s'allumer. */
    @Test
    void aCommentAloneNeverMarksTheSessionAsAwaiting() throws Exception {
        String workoutId = commentedWorkout("Belle séance.");

        assertThat(coachWorkout(workoutId).hasNonNull("athleteReplyAt")).isFalse();
    }

    // --- 2. Le fil se lit depuis la séance ----------------------------------------------------

    @Test
    void theCoachReadsTheReplyOnTheSessionItself() throws Exception {
        String workoutId = commentedWorkout("Comment tu as senti les allures ?");
        reply(workoutId, "Un peu dur sur les deux dernières.");

        JsonNode thread = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workouts/{w}/thread", clubId, athleteId(), workoutId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(thread).as("le fil de la séance n'est pas vide").isNotEmpty();
        assertThat(thread.get(0).get("workoutId").asText())
                .as("il ne porte que ce qui parle de cette séance").isEqualTo(workoutId);
        assertThat(thread.get(0).get("senderRole").asText()).isEqualTo("ATHLETE");
    }

    /** L'athlète relit le même fil : c'est le même échange, vu de l'autre bout. */
    @Test
    void theAthleteSeesTheSameThread() throws Exception {
        String workoutId = commentedWorkout("Alors ?");
        reply(workoutId, "Nickel.");

        JsonNode thread = objectMapper.readTree(mvc.perform(
                        get("/me/workouts/{w}/thread", workoutId)
                                .header("Authorization", athleteBearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(thread).hasSize(1);
    }

    // --- 3. Le cockpit le dit, et l'alerte mène à la séance ------------------------------------

    @Test
    void anUnreadReplyRaisesADashboardAlertThatLeadsToTheSession() throws Exception {
        String workoutId = commentedWorkout("Tu as bien récupéré ?");
        reply(workoutId, "Oui, ça va mieux.");

        JsonNode alert = replyAlert();
        assertThat(alert).as("le cockpit signale la réponse en attente").isNotNull();
        assertThat(alert.get("action").get("link").asText())
                .as("et mène à la séance où la question a été posée")
                .isEqualTo("/app/athletes/" + athleteId() + "/workouts/" + workoutId);
    }

    /** Le corps de l'alerte nomme la séance, jamais le texte : le cockpit s'ouvre en salle. */
    @Test
    void theDashboardAlertNeverCarriesTheReplyItself() throws Exception {
        String workoutId = commentedWorkout("Ton genou ?");
        reply(workoutId, "Il me lance encore un peu.");

        assertThat(replyAlert().toString()).doesNotContain("lance");
    }

    @Test
    void readingTheReplyClearsTheAlert() throws Exception {
        String workoutId = commentedWorkout("Alors, cette séance ?");
        reply(workoutId, "Très bien !");
        assertThat(replyAlert()).isNotNull();

        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts/{w}/reply/read",
                        clubId, athleteId(), workoutId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isOk());

        assertThat(replyAlert()).as("l'alerte s'éteint une fois la réponse lue").isNull();
    }

    /**
     * Idempotence, comme du côté athlète : sans elle, « répondu il y a trois jours, lu ce matin »
     * redeviendrait « lu à l'instant » à chaque ouverture de la fiche.
     */
    @Test
    void readingTwiceKeepsTheFirstReadTimestamp() throws Exception {
        String workoutId = commentedWorkout("Ça va ?");
        reply(workoutId, "Oui.");

        String first = markReplyRead(workoutId).get("athleteReplyReadAt").asText();
        String second = markReplyRead(workoutId).get("athleteReplyReadAt").asText();

        assertThat(second).isEqualTo(first);
    }

    /** Répondre, c'est avoir lu : le coach n'a pas à faire deux gestes pour un seul échange. */
    @Test
    void theCoachAnsweringFromTheSessionClosesTheWait() throws Exception {
        String workoutId = commentedWorkout("Comment tu te sens ?");
        reply(workoutId, "Fatigué mais content.");

        mvc.perform(post("/clubs/{c}/athletes/{a}/messages", clubId, athleteId())
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("body", "On lèvera le pied demain.", "workoutId", workoutId))))
                .andExpect(status().isCreated());

        assertThat(coachWorkout(workoutId).hasNonNull("athleteReplyReadAt")).isTrue();
        assertThat(replyAlert()).isNull();
    }

    /** Une deuxième réponse est un deuxième message : elle rouvre l'attente. */
    @Test
    void asecondReplyReopensTheWait() throws Exception {
        String workoutId = commentedWorkout("Tu confirmes pour dimanche ?");
        reply(workoutId, "Oui.");
        markReplyRead(workoutId);
        assertThat(replyAlert()).isNull();

        reply(workoutId, "Ah non, finalement je ne peux pas.");

        assertThat(replyAlert()).as("le second mot se signale comme le premier").isNotNull();
    }

    // --- 4. La notification mène à la séance, pas à la messagerie -----------------------------

    @Test
    void theCoachNotificationLinksToTheSessionNotTheInbox() throws Exception {
        String workoutId = commentedWorkout("Tu te sentais comment ?");
        reply(workoutId, "Bien !");

        JsonNode notification = coachNotificationOfType("WORKOUT_REPLY");
        assertThat(notification).as("une notification propre est déposée").isNotNull();
        assertThat(notification.get("link").asText())
                .isEqualTo("/app/athletes/" + athleteId() + "/workouts/" + workoutId);
    }

    /** Le corps ne porte que le nom de l'expéditeur : une réponse peut parler de douleur. */
    @Test
    void theCoachNotificationNeverCarriesTheReplyItself() throws Exception {
        String workoutId = commentedWorkout("Et ce mollet ?");
        reply(workoutId, "Le mollet tire toujours.");

        assertThat(coachNotificationOfType("WORKOUT_REPLY").toString()).doesNotContain("tire");
    }

    // --- Utilitaires --------------------------------------------------------------------------

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String athleteBearer() throws Exception {
        return "Bearer " + login(DemoSeedService.ATHLETE_EMAIL).get("accessToken").asText();
    }

    private String athleteId() throws Exception {
        return login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText();
    }

    private String commentedWorkout(String text) throws Exception {
        String workoutId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId())
                                .header("Authorization", coachBearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scheduledDate\":\"" + LocalDate.now().minusDays(2) + "\","
                                        + "\"type\":\"ENDURANCE\",\"title\":\"Sortie commentée\","
                                        + "\"targetDurationS\":3600}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(patch("/clubs/{c}/athletes/{a}/workouts/{w}/coach-comment",
                        clubId, athleteId(), workoutId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", text))))
                .andExpect(status().isOk());
        return workoutId;
    }

    private void reply(String workoutId, String body) throws Exception {
        mvc.perform(post("/me/messages")
                        .header("Authorization", athleteBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("body", body, "workoutId", workoutId))))
                .andExpect(status().isCreated());
    }

    private JsonNode coachWorkout(String workoutId) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workouts/{w}", clubId, athleteId(), workoutId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode markReplyRead(String workoutId) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts/{w}/reply/read",
                                clubId, athleteId(), workoutId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** L'alerte « t'a répondu » du cockpit, ou null. */
    private JsonNode replyAlert() throws Exception {
        JsonNode alerts = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/dashboard/alerts", clubId).header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode a : alerts) {
            if ("REPLY".equals(a.get("type").asText())) {
                return a;
            }
        }
        return null;
    }

    private JsonNode coachNotificationOfType(String type) throws Exception {
        JsonNode body = objectMapper.readTree(mvc.perform(get("/notifications")
                        .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode list = body.has("content") ? body.get("content") : body;
        List<String> seen = new ArrayList<>();
        for (JsonNode row : list) {
            seen.add(row.get("type").asText());
            if (type.equals(row.get("type").asText())) {
                return row;
            }
        }
        assertThat(seen).as("types de notifications déposées pour le coach").isNotNull();
        return null;
    }
}
