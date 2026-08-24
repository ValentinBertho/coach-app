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
 * Centre de notifications in-app : planifier une séance crée une notification visible par
 * l'athlète, dénombrée comme non lue puis marquable comme lue. Scopé par utilisateur.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationCenterTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String coachBearer;
    private String athleteBearer;
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
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private long unread() throws Exception {
        return objectMapper.readTree(mvc.perform(get("/notifications/unread-count")
                        .header("Authorization", athleteBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("count").asLong();
    }

    @Test
    void plannedWorkoutCreatesNotificationForAthlete() throws Exception {
        long before = unread();

        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"" + LocalDate.now()
                                + "\",\"type\":\"ENDURANCE\",\"title\":\"Footing du jour\"}"))
                .andExpect(status().isCreated());

        assertThat(unread()).isEqualTo(before + 1);

        JsonNode page = objectMapper.readTree(mvc.perform(get("/notifications")
                        .header("Authorization", athleteBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode latest = page.get("content").get(0);
        assertThat(latest.get("type").asText()).isEqualTo("WORKOUT_PLANNED");
        assertThat(latest.get("read").asBoolean()).isFalse();
        String id = latest.get("id").asText();

        mvc.perform(post("/notifications/{id}/read", id).header("Authorization", athleteBearer))
                .andExpect(status().isNoContent());
        assertThat(unread()).isEqualTo(before);
    }

    @Test
    void streamEndpointStartsAsync() throws Exception {
        mvc.perform(get("/notifications/stream").header("Authorization", athleteBearer))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted());
    }

    @Test
    void notificationPreferencesDefaultTrueAndUpdate() throws Exception {
        JsonNode def = objectMapper.readTree(mvc.perform(get("/notifications/preferences")
                        .header("Authorization", athleteBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(def.get("emailEnabled").asBoolean()).isTrue();
        assertThat(def.get("pushEnabled").asBoolean()).isTrue();

        JsonNode upd = objectMapper.readTree(mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/notifications/preferences")
                                .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pushEnabled\":false}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(upd.get("pushEnabled").asBoolean()).isFalse();
        assertThat(upd.get("emailEnabled").asBoolean()).isTrue();

        JsonNode after = objectMapper.readTree(mvc.perform(get("/notifications/preferences")
                        .header("Authorization", athleteBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(after.get("pushEnabled").asBoolean()).isFalse();
    }

    @Test
    void notificationsAreScopedToTheUser() throws Exception {
        // Le coach ne voit pas les notifications de l'athlète (chacun son centre).
        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"" + LocalDate.now()
                                + "\",\"type\":\"ENDURANCE\",\"title\":\"Footing\"}"))
                .andExpect(status().isCreated());

        JsonNode coachPage = objectMapper.readTree(mvc.perform(get("/notifications")
                        .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode n : coachPage.get("content")) {
            assertThat(n.get("type").asText()).isNotEqualTo("WORKOUT_PLANNED");
        }
    }

    /**
     * Attribuer un plan de six séances ne produit <b>qu'une</b> notification.
     *
     * <p>C'est le correctif qui justifie tout le reste : chaque séance générée descendait jusqu'à
     * la création unitaire, qui notifiait. Un plan de douze semaines à quatre séances en envoyait
     * une cinquantaine d'un coup — et la méthode étant régénérante, chaque réattribution rejouait
     * la salve. Le test passe par l'API de bout en bout, parce que le défaut n'était pas dans le
     * service de notification mais dans le chemin qui y mène.</p>
     */
    @Test
    void assigningAPlanNotifiesOnceForTheWholeProgram() throws Exception {
        JsonNode templates = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/workout-templates", clubId).header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        String templateId = templates.get("content").get(0).get("id").asText();

        StringBuilder items = new StringBuilder();
        for (int week = 0; week < 3; week++) {
            for (int day : new int[] {2, 5}) {
                items.append(items.isEmpty() ? "" : ",")
                        .append("{\"weekIndex\":").append(week)
                        .append(",\"dayOfWeek\":").append(day)
                        .append(",\"kind\":\"COURSE\",\"templateId\":\"").append(templateId).append("\"}");
            }
        }
        String planId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/training-plans", clubId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Prépa 10 km\",\"durationWeeks\":3,\"items\":[" + items + "]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray())
                .get("id").asText();

        long before = unread();
        JsonNode applied = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/training-plans/{p}/apply", clubId, planId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"athleteId\":\"" + athleteId + "\",\"startDate\":\""
                                        + LocalDate.now().plusDays(1) + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());

        assertThat(applied.get("created").asInt()).isEqualTo(6);
        assertThat(unread()).isEqualTo(before + 1);

        JsonNode latest = latestNotification();
        assertThat(latest.get("type").asText()).isEqualTo("PLAN_ASSIGNED");
        assertThat(latest.get("body").asText()).contains("Prépa 10 km").contains("6 séances");
    }

    /**
     * Déplacer une séance prévient l'athlète. Le geste le plus courant du calendrier — glisser une
     * séance d'un jour à l'autre — ne passait jusqu'ici aucun signal.
     */
    @Test
    void reschedulingAWorkoutNotifiesTheAthlete() throws Exception {
        String workoutId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scheduledDate\":\"" + LocalDate.now().plusDays(2)
                                        + "\",\"type\":\"ENDURANCE\",\"title\":\"Sortie longue\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray())
                .get("id").asText();

        long before = unread();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/clubs/{c}/athletes/{a}/workouts/{w}/reschedule", clubId, athleteId, workoutId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"" + LocalDate.now().plusDays(4) + "\"}"))
                .andExpect(status().isOk());

        assertThat(unread()).isEqualTo(before + 1);
        JsonNode latest = latestNotification();
        assertThat(latest.get("type").asText()).isEqualTo("WORKOUT_UPDATED");
        assertThat(latest.get("title").asText()).isEqualTo("Séance déplacée");
    }

    /**
     * Un message du coach atteint l'athlète hors de l'application, et <b>sans en citer le
     * contenu</b> : le fil coach ↔ athlète parle de blessures et de fatigue.
     */
    @Test
    void coachMessageNotifiesAthleteWithoutQuotingIt() throws Exception {
        long before = unread();

        mvc.perform(post("/clubs/{c}/athletes/{a}/messages", clubId, athleteId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Comment va ton genou ?\"}"))
                .andExpect(status().isCreated());

        assertThat(unread()).isEqualTo(before + 1);
        JsonNode latest = latestNotification();
        assertThat(latest.get("type").asText()).isEqualTo("NEW_MESSAGE");
        assertThat(latest.get("body").asText()).doesNotContain("genou");
        // Le lien mène au FIL et non à l'écran : un athlète en a désormais plusieurs — un par
        // coach, plus son groupe et le club — et « Messages » ne dirait pas lequel s'est animé.
        assertThat(latest.get("link").asText()).startsWith("/athlete/messages?c=");

        // Anti-rafale : le deuxième message de la salve ne resonne pas.
        mvc.perform(post("/clubs/{c}/athletes/{a}/messages", clubId, athleteId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Et pour dimanche ?\"}"))
                .andExpect(status().isCreated());
        assertThat(unread()).isEqualTo(before + 1);
    }

    /**
     * Les préférences fines font l'aller-retour, et couper une famille fait taire son push
     * <b>sans</b> vider le centre — c'est l'invariant qui rend le réglage acceptable : on retire
     * l'interruption, jamais l'information.
     */
    @Test
    void mutingACategoryKeepsTheNotificationCentreFed() throws Exception {
        JsonNode saved = objectMapper.readTree(mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/notifications/preferences")
                                .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mutedCategories\":[\"PROGRAMME\"],\"quietStart\":\"23:00\","
                                        + "\"quietEnd\":\"06:30\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(saved.get("mutedCategories")).hasSize(1);
        assertThat(saved.get("mutedCategories").get(0).asText()).isEqualTo("PROGRAMME");
        assertThat(saved.get("quietStart").asText()).isEqualTo("23:00");
        assertThat(saved.get("quietEnd").asText()).isEqualTo("06:30");

        long before = unread();
        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"" + LocalDate.now()
                                + "\",\"type\":\"ENDURANCE\",\"title\":\"Footing\"}"))
                .andExpect(status().isCreated());

        // La famille est coupée : le push ne part pas, mais la ligne est bien là.
        assertThat(unread()).isEqualTo(before + 1);
        assertThat(latestNotification().get("type").asText()).isEqualTo("WORKOUT_PLANNED");

        // Et la liste vide réactive tout.
        JsonNode reset = objectMapper.readTree(mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/notifications/preferences")
                                .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mutedCategories\":[]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(reset.get("mutedCategories")).isEmpty();
    }

    /** Notification la plus récente de l'athlète. */
    private JsonNode latestNotification() throws Exception {
        return objectMapper.readTree(mvc.perform(get("/notifications")
                        .header("Authorization", athleteBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray())
                .get("content").get(0);
    }
}
