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
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Charge d'entraînement bout-en-bout : une séance datée du jour, renseignée (RPE + durée),
 * produit une charge aiguë et un ratio ACWR cohérents.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AthleteLoadControllerTest {

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

    @Test
    void computesLoadFromFeedback() throws Exception {
        String today = LocalDate.now().toString();

        // Séance du jour de 60 min pour l'athlète démo.
        String workoutId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scheduledDate\":\"" + today
                                        + "\",\"type\":\"ENDURANCE\",\"title\":\"EF\",\"targetDurationS\":3600}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // Retour athlète : RPE 6 → charge 6 × 60 = 360.
        mvc.perform(patch("/me/workouts/{w}/feedback", workoutId)
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"rpe\":6}"))
                .andExpect(status().isOk());

        JsonNode load = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/load", clubId, athleteId).header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        // Au moins la séance ajoutée (360) ; d'autres séances de démo peuvent s'y ajouter.
        assertThat(load.get("acuteLoad7d").asDouble()).isGreaterThanOrEqualTo(360.0);
        assertThat(load.get("sessions7d").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(load.get("ratio").isNull()).isFalse();
        double d1 = load.get("distribution7d").get("domain1Pct").asDouble();
        double d2 = load.get("distribution7d").get("domain2Pct").asDouble();
        double d3 = load.get("distribution7d").get("domain3Pct").asDouble();
        assertThat(d1 + d2 + d3).isGreaterThan(0);
    }

    // --- Charge sur la durée réalisée (V0-02 / V0-03) --------------------------

    /** Charge aiguë courante — sert de référence pour mesurer l'apport d'une seule séance. */
    private double acuteLoad() throws Exception {
        return objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/load", clubId, athleteId).header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("acuteLoad7d").asDouble();
    }

    /** Planifie une séance d'une heure aujourd'hui et renvoie son identifiant. */
    private String planOneHourSession(String title) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scheduledDate\":\"" + LocalDate.now()
                                        + "\",\"type\":\"ENDURANCE\",\"title\":\"" + title
                                        + "\",\"targetDurationS\":3600}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    /**
     * Une séance écourtée ne doit peser que ce qu'elle a duré.
     *
     * <p>La charge valait {@code RPE × durée prescrite} sans jamais regarder ce qui avait été fait :
     * une sortie d'une heure abandonnée à 20 minutes comptait 360 UA au lieu de 120, et deux
     * abandons dans la semaine suffisaient à déclencher « charge en forte hausse » sur un athlète
     * qui s'était justement entraîné moins.</p>
     */
    @Test
    void shortenedSessionCountsItsActualDurationNotThePrescribedOne() throws Exception {
        double before = acuteLoad();
        String workoutId = planOneHourSession("Sortie écourtée");

        // 20 minutes réellement courues à RPE 6 → 120 UA, et non 6 × 60 = 360.
        mvc.perform(patch("/me/workouts/{w}/feedback", workoutId)
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PARTIAL\",\"rpe\":6,\"actualDurationS\":1200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.actualDurationS").value(1200));

        assertThat(acuteLoad() - before).isCloseTo(120.0, within(0.5));
    }

    /**
     * Une séance déclarée non faite n'a produit aucune charge — et l'athlète doit pouvoir le dire.
     * Il n'avait jusqu'ici que « réalisée » et « partiellement » : son seul recours était le
     * silence, qui ressortait quelques jours plus tard en alerte « séance manquée », sans motif.
     */
    @Test
    void missedSessionCarriesItsReasonAndAddsNoLoad() throws Exception {
        double before = acuteLoad();
        String workoutId = planOneHourSession("Sortie non faite");

        mvc.perform(patch("/me/workouts/{w}/feedback", workoutId)
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MISSED\",\"missedReason\":\"UNEXPECTED\","
                                + "\"comment\":\"Déplacement pro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MISSED"))
                .andExpect(jsonPath("$.missedReason").value("UNEXPECTED"))
                .andExpect(jsonPath("$.rpe").doesNotExist());

        assertThat(acuteLoad() - before).isCloseTo(0.0, within(0.5));
    }

    /**
     * Une séance menée à son terme continue de peser sa durée prescrite : le repli reste le
     * comportement par défaut, et l'historique antérieur à la migration n'est pas recalculé.
     */
    @Test
    void completedSessionStillUsesThePrescribedDuration() throws Exception {
        double before = acuteLoad();
        String workoutId = planOneHourSession("Sortie complète");

        mvc.perform(patch("/me/workouts/{w}/feedback", workoutId)
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"rpe\":5}"))
                .andExpect(status().isOk());

        assertThat(acuteLoad() - before).isCloseTo(300.0, within(0.5));
    }
}
