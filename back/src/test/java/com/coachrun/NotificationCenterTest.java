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
}
