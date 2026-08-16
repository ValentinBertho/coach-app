package com.coachrun;

import com.coachrun.entity.enums.NotificationCategory;
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
 * Le « vu 👏 » du coach : la plus petite réponse possible à un retour d'athlète.
 *
 * <p>Ce qui est vérifié tient en une distinction : « traité » et « vu » vident tous deux la file
 * du coach, mais un seul des deux revient à l'athlète. Les confondre reviendrait à notifier
 * l'athlète chaque fois qu'un coach fait le ménage.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachAcknowledgementTest {

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

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** Une séance de l'athlète de démonstration, réalisée et commentée : de quoi être applaudie. */
    private String debriefedWorkout() throws Exception {
        String athleteId = athleteOfDemoUser();
        String workoutId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", coachBearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scheduledDate\":\"" + LocalDate.now() + "\","
                                        + "\"type\":\"ENDURANCE\",\"title\":\"Footing applaudi\","
                                        + "\"targetDurationS\":2700}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // Le débrief est déclaré par l'athlète depuis son portail, jamais par le coach.
        mvc.perform(patch("/me/workouts/{w}/feedback", workoutId)
                        .header("Authorization", athleteBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"rpe\":6,\"feel\":2,"
                                + "\"comment\":\"Bien passé\"}"))
                .andExpect(status().isOk());
        return workoutId;
    }

    private String athleteBearer() throws Exception {
        return "Bearer " + login(DemoSeedService.ATHLETE_EMAIL).get("accessToken").asText();
    }

    /** L'athlète relié au compte de démonstration — celui dont on peut lire les notifications. */
    private String athleteOfDemoUser() throws Exception {
        return login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText();
    }

    private JsonNode athleteNotifications() throws Exception {
        return objectMapper.readTree(mvc.perform(get("/notifications")
                        .header("Authorization", athleteBearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private long acknowledgements(JsonNode notifications) {
        JsonNode list = notifications.has("content") ? notifications.get("content") : notifications;
        long n = 0;
        for (JsonNode row : list) {
            if ("COACH_ACK".equals(row.get("type").asText())) {
                n++;
            }
        }
        return n;
    }

    @Test
    void acknowledgingTellsTheAthleteAndClearsTheQueue() throws Exception {
        String athleteId = athleteOfDemoUser();
        String workoutId = debriefedWorkout();

        JsonNode workout = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts/{w}/acknowledge",
                                clubId, athleteId, workoutId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        // La date reste sur la séance : c'est ce qui distingue le geste d'une notification, qui
        // passe et s'oublie.
        assertThat(workout.get("coachAcknowledgedAt").isNull()).isFalse();
        // Applaudir traite le retour : le coach ne doit pas avoir à faire les deux gestes.
        assertThat(acknowledgements(athleteNotifications())).isEqualTo(1);
    }

    /**
     * Ré-applaudir ne renotifie pas. Sans cette garde, un double clic — ou le rejeu de l'action
     * rapide d'une notification — enverrait deux félicitations pour une séance, ce qui les
     * décrédibilise toutes les deux.
     */
    @Test
    void acknowledgingTwiceNotifiesOnlyOnce() throws Exception {
        String athleteId = athleteOfDemoUser();
        String workoutId = debriefedWorkout();
        String url = "/clubs/{c}/athletes/{a}/workouts/{w}/acknowledge";

        mvc.perform(post(url, clubId, athleteId, workoutId).header("Authorization", coachBearer))
                .andExpect(status().isOk());
        mvc.perform(post(url, clubId, athleteId, workoutId).header("Authorization", coachBearer))
                .andExpect(status().isOk());

        assertThat(acknowledgements(athleteNotifications())).isEqualTo(1);
    }

    /**
     * « Traité » reste silencieux. C'est le point de tout ce chantier : le coach a le droit de
     * classer un retour sans rien en dire, et l'athlète ne doit pas recevoir une reconnaissance
     * qui n'a pas été voulue.
     */
    @Test
    void markingAsReviewedSaysNothingToTheAthlete() throws Exception {
        String athleteId = athleteOfDemoUser();
        String workoutId = debriefedWorkout();

        mvc.perform(patch("/clubs/{c}/athletes/{a}/workouts/{w}/reviewed", clubId, athleteId, workoutId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isOk());

        assertThat(acknowledgements(athleteNotifications())).isZero();
    }

    /**
     * Le « vu » se règle avec les messages, pas avec le suivi : couper les alertes de suivi ne
     * doit pas couper la seule reconnaissance que l'athlète reçoive de son coach.
     */
    @Test
    void acknowledgementIsSettledWithMessagesNotWithMonitoring() {
        assertThat(NotificationCategory.of("COACH_ACK")).isEqualTo(NotificationCategory.MESSAGES);
    }
}
