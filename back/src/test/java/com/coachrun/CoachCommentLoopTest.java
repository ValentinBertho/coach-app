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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La boucle du mot du coach : il écrit, l'athlète l'apprend, le lit, et peut répondre.
 *
 * <p><b>Ce que ces tests protègent.</b> Un coach pilote a écrit « tu te sentais facile sur les
 * allures ? » sur une sortie de l'avant-veille. Son athlète ne l'a jamais su : la notification
 * menait à une liste générique, aucun écran ne pouvait signaler un mot non lu, et il n'y avait
 * nulle part où répondre. Il a fallu qu'il rouvre la sortie par hasard — ce qui suppose de
 * savoir qu'il y avait quelque chose à découvrir.</p>
 *
 * <p>Chacun des trois maillons est vérifié ici séparément, parce que la panne était silencieuse
 * de bout en bout : rien n'échouait, la conversation s'arrêtait, c'est tout.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachCommentLoopTest {

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

    // --- 1. La notification mène à la séance, pas à une liste --------------------------------

    /**
     * Le défaut d'origine : le lien pointait sur {@code /athlete/history}. La notification
     * arrivait, l'athlète la touchait, et tombait sur un historique où rien ne distinguait la
     * séance commentée.
     */
    @Test
    void theNotificationLinksToTheSessionItself() throws Exception {
        String workoutId = commentedWorkout("Tu te sentais facile sur les allures ?");

        JsonNode notification = firstNotificationOfType("COACH_COMMENT");
        assertThat(notification).as("une notification est bien déposée").isNotNull();
        assertThat(notification.get("link").asText())
                .as("le lien ouvre la séance commentée")
                .isEqualTo("/athlete/workouts/" + workoutId);
    }

    /** Le corps porte le titre de la séance — jamais le commentaire, qui s'affiche écran verrouillé. */
    @Test
    void theNotificationNeverCarriesTheCommentItself() throws Exception {
        commentedWorkout("Ton genou te fait encore mal ?");

        JsonNode notification = firstNotificationOfType("COACH_COMMENT");
        assertThat(notification.toString())
                .as("le contenu du mot ne sort pas dans la notification")
                .doesNotContain("genou");
    }

    // --- 2. Le mot a un état : non lu, puis lu -----------------------------------------------

    @Test
    void anUnreadCommentSurfacesUntilItIsOpened() throws Exception {
        String workoutId = commentedWorkout("Belle séance.");

        assertThat(unreadIds()).as("il remonte tant qu'il n'est pas lu").contains(workoutId);

        mvc.perform(post("/me/workouts/{w}/coach-comment/read", workoutId)
                        .header("Authorization", athleteBearer()))
                .andExpect(status().isOk());

        assertThat(unreadIds()).as("il disparaît une fois ouvert").doesNotContain(workoutId);
    }

    /**
     * Idempotence : sans elle, « lu il y a trois jours » redeviendrait « lu à l'instant » à chaque
     * consultation, et le coach perdrait tout moyen de savoir quand son message est arrivé.
     */
    @Test
    void readingTwiceKeepsTheFirstReadTimestamp() throws Exception {
        String workoutId = commentedWorkout("Bien joué.");

        String first = readComment(workoutId).get("coachCommentReadAt").asText();
        String second = readComment(workoutId).get("coachCommentReadAt").asText();

        assertThat(second).as("la date de première lecture ne bouge plus").isEqualTo(first);
    }

    /** Un coach qui réécrit pose un nouveau message : l'athlète doit être averti des deux. */
    @Test
    void aRewrittenCommentBecomesUnreadAgain() throws Exception {
        String workoutId = commentedWorkout("Première remarque.");
        mvc.perform(post("/me/workouts/{w}/coach-comment/read", workoutId)
                        .header("Authorization", athleteBearer()))
                .andExpect(status().isOk());
        assertThat(unreadIds()).doesNotContain(workoutId);

        comment(workoutId, "En fait, une deuxième remarque.");

        assertThat(unreadIds()).as("le second mot se signale comme le premier").contains(workoutId);
    }

    // --- 3. Répondre, sans ouvrir un second canal --------------------------------------------

    /**
     * La réponse part dans le fil de messagerie, rattachée à la séance. Un canal propre à la
     * séance aurait donné au coach deux endroits où regarder — c'est ainsi qu'un message se perd.
     */
    @Test
    void theAthleteCanAnswerAndTheCoachSeesItInTheThread() throws Exception {
        String workoutId = commentedWorkout("Tu te sentais facile sur les allures ?");

        // Sérialisé par Jackson plutôt qu'écrit à la main : `content(String)` encode dans le jeu
        // de caractères par défaut de la plateforme, et les accents en ressortent abîmés.
        mvc.perform(post("/me/messages")
                        .header("Authorization", athleteBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("body", REPLY, "workoutId", workoutId))))
                .andExpect(status().isCreated());

        JsonNode thread = objectMapper.readTree(mvc.perform(get("/me/messages")
                        .header("Authorization", athleteBearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        // On retrouve la réponse par la séance qu'elle porte, et non par son texte : MockMvc lit
        // le corps de réponse dans le jeu de caractères par défaut de la plateforme, si bien
        // qu'une comparaison de chaîne accentuée testerait l'encodage du test, pas le produit.
        JsonNode answer = null;
        for (JsonNode m : thread) {
            if (m.hasNonNull("workoutId") && workoutId.equals(m.get("workoutId").asText())) {
                answer = m;
            }
        }
        assertThat(answer)
                .as("la réponse est dans le fil, rattachée à la séance commentée")
                .isNotNull();
        assertThat(answer.get("senderRole").asText())
                .as("c'est bien l'athlète qui a parlé")
                .isEqualTo("ATHLETE");
        assertThat(answer.get("body").asText()).as("elle porte un texte").isNotBlank();
    }

    /** Réponse type de l'athlète — avec accents, précisément parce que c'est ce qui casse. */
    private static final String REPLY = "Oui, très facile !";

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

    /** Une séance de l'athlète de démonstration, sur laquelle le coach a laissé un mot. */
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
        comment(workoutId, text);
        return workoutId;
    }

    private void comment(String workoutId, String text) throws Exception {
        mvc.perform(patch("/clubs/{c}/athletes/{a}/workouts/{w}/coach-comment",
                        clubId, athleteId(), workoutId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("comment", text))))
                .andExpect(status().isOk());
    }

    private JsonNode readComment(String workoutId) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        post("/me/workouts/{w}/coach-comment/read", workoutId)
                                .header("Authorization", athleteBearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private java.util.List<String> unreadIds() throws Exception {
        JsonNode list = objectMapper.readTree(mvc.perform(get("/me/coach-comments/unread")
                        .header("Authorization", athleteBearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        java.util.List<String> ids = new java.util.ArrayList<>();
        list.forEach(w -> ids.add(w.get("id").asText()));
        return ids;
    }

    private JsonNode firstNotificationOfType(String type) throws Exception {
        JsonNode body = objectMapper.readTree(mvc.perform(get("/notifications")
                        .header("Authorization", athleteBearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode list = body.has("content") ? body.get("content") : body;
        for (JsonNode row : list) {
            if (type.equals(row.get("type").asText())) {
                return row;
            }
        }
        return null;
    }
}
