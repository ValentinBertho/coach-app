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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ce que devient une séance une fois qu'elle a été faite, quand le réalisé arrive d'une montre.
 *
 * <p>Deux trous se rejoignaient et rendaient le suivi muet pour un club qui synchronise Strava.</p>
 *
 * <ol>
 *   <li><b>Une séance structurée repartait sans volume cible.</b> Les totaux d'une séance ne
 *       retenaient que les blocs <i>calculables</i> pour l'athlète : un athlète dont le profil ne
 *       permet pas encore de calculer d'allure recevait une séance à {@code targetDistanceM} et
 *       {@code targetDurationS} nuls. Or une séance sans volume n'est comparable à rien : le
 *       rapprochement automatique la laisse de côté par construction, elle reste
 *       {@code PLANNED} pour toujours, et le coach ne retrouve jamais ce qui a été fait.</li>
 *   <li><b>Une sortie sans séance en face ne pesait nulle part.</b> Charge, ACWR et répartition
 *       d'intensité ne lisaient que les séances prescrites. Un athlète qui s'entraîne par sa montre
 *       affichait une charge quasi nulle en regard d'un volume bien réel.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StravaFollowUpTest {

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
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** Modèle DARI Lab : des blocs, aucune cible de volume propre au modèle. */
    private String structuredTemplate(String mainBlockJson) throws Exception {
        String id = objectMapper.readTree(mvc.perform(post("/clubs/{c}/workout-templates", clubId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Structure " + java.util.UUID.randomUUID()
                                + "\",\"type\":\"THRESHOLD\",\"title\":\"Seuil\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(put("/clubs/{c}/workout-templates/{t}/structure", clubId, id)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"structure\":{\"warmup\":[],\"main\":[" + mainBlockJson + "],\"cooldown\":[]}}"))
                .andExpect(status().isOk());
        return id;
    }

    private JsonNode schedule(String templateId, String date) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workout-templates/{t}/schedule", clubId, athleteId, templateId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"date\":\"" + date + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /**
     * Le volume écrit dans les blocs ne dépend pas de l'athlète : 20 minutes durent 20 minutes
     * pour tout le monde. La séance doit repartir avec, même quand aucune allure n'est calculable.
     */
    @Test
    void aStructuredSessionCarriesTheVolumeWrittenInItsBlocks() throws Exception {
        String templateId = structuredTemplate("{\"type\":\"threshold\",\"durationS\":1200}");

        JsonNode workout = schedule(templateId, "2026-08-20");

        assertThat(workout.get("targetDurationS").asInt()).isEqualTo(1200);
    }

    /** Répétitions et séries comprises : « 2 × (6 × 1000 m) » vaut 12 km de cible. */
    @Test
    void theWrittenVolumeCountsRepetitionsAndSets() throws Exception {
        String templateId = structuredTemplate(
                "{\"type\":\"intervals\",\"reps\":6,\"distanceM\":1000,\"sets\":2}");

        JsonNode workout = schedule(templateId, "2026-08-21");

        assertThat(workout.get("targetDistanceM").asInt()).isEqualTo(12000);
    }

    /**
     * Le parcours complet, celui qui manquait : le coach prescrit, l'athlète court, la montre
     * remonte la sortie — et la séance passe enfin « réalisée ».
     */
    @Test
    void anImportedActivityFinallyMatchesAStructuredSession() throws Exception {
        String templateId = structuredTemplate("{\"type\":\"threshold\",\"durationS\":3000}");
        schedule(templateId, "2026-08-22");

        JsonNode activity = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"activityDate\":\"2026-08-22\",\"title\":\"Sortie\",\"distanceM\":10200,"
                                        + "\"durationS\":3050,\"source\":\"STRAVA\",\"externalId\":\"strava-1\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(activity.get("status").asText()).isEqualTo("MATCHED");
        assertThat(activity.get("matchedWorkoutId").isNull()).isFalse();

        JsonNode workouts = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workouts?from=2026-08-22&to=2026-08-22", clubId, athleteId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(workouts.get(0).get("status").asText()).isIn("COMPLETED", "PARTIAL");
    }

    /**
     * Sortie libre, notée par l'athlète, sans séance en face : elle doit peser dans la charge.
     * C'est le quotidien d'un athlète qui synchronise sa montre sans plan écrit.
     */
    @Test
    void aRatedActivityWithoutAPlannedSessionCountsInTheLoad() throws Exception {
        String activityId = objectMapper.readTree(mvc.perform(post("/me/activities")
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityDate\":\"" + LocalDate.now() + "\",\"title\":\"Sortie libre\","
                                + "\"distanceM\":12000,\"durationS\":3600}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        // Le jeu de démonstration peut porter une séance ce jour-là : on détache pour être bien
        // dans le cas visé — une sortie qui n'a aucune séance en face.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/clubs/{c}/athletes/{a}/activities/{id}/match", clubId, athleteId, activityId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isOk());

        // Tant qu'elle n'est pas notée, il n'y a pas d'intensité à porter : rien ne change.
        double before = acuteLoad();

        mvc.perform(patch("/me/activities/{id}", activityId)
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rpe\":6}"))
                .andExpect(status().isOk());

        // RPE 6 × 60 min = 360 UA.
        assertThat(acuteLoad()).isEqualTo(before + 360.0);
    }

    /** Une sortie déjà rattachée pèse par sa séance : elle ne doit pas compter deux fois. */
    @Test
    void aMatchedActivityIsNotCountedTwice() throws Exception {
        String workoutId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scheduledDate\":\"" + LocalDate.now()
                                        + "\",\"type\":\"ENDURANCE\",\"title\":\"EF\",\"targetDurationS\":3600}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(patch("/me/workouts/{w}/feedback", workoutId)
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"rpe\":6}"))
                .andExpect(status().isOk());
        double withSession = acuteLoad();

        // La sortie correspondante arrive de la montre : elle se rattache à la séance.
        JsonNode activity = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"activityDate\":\"" + LocalDate.now() + "\",\"title\":\"Sortie\","
                                        + "\"distanceM\":12000,\"durationS\":3600,\"source\":\"STRAVA\","
                                        + "\"externalId\":\"strava-2\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(activity.get("status").asText()).isEqualTo("MATCHED");

        assertThat(acuteLoad()).isEqualTo(withSession);
    }

    /**
     * L'ordre réel des choses : on court, on note sa séance en rentrant, <b>et la montre se
     * synchronise après</b>. La sortie doit retrouver la séance que l'athlète vient de clore.
     *
     * <p>Le rapprochement ne regardait que les séances encore {@code PLANNED} : noter sa séance
     * la rendait donc introuvable pour la sortie qui arrivait derrière. Elle restait « à
     * rattacher », la séance restait sans réalisé, et aucun des deux ne racontait l'autre — le
     * « je ne retrouve plus les séances une fois faites » remonté en bêta.</p>
     */
    @Test
    void uneSortieRetrouveLaSeanceDejaNoteeParLAthlete() throws Exception {
        String workoutId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scheduledDate\":\"" + LocalDate.now()
                                        + "\",\"type\":\"ENDURANCE\",\"title\":\"EF\","
                                        + "\"targetDistanceM\":12000,\"targetDurationS\":3600}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // L'athlète clôt sa séance dans l'application, avant toute synchronisation.
        mvc.perform(patch("/me/workouts/{w}/feedback", workoutId)
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"rpe\":6}"))
                .andExpect(status().isOk());

        JsonNode activity = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"activityDate\":\"" + LocalDate.now() + "\",\"title\":\"Sortie\","
                                        + "\"distanceM\":12000,\"durationS\":3600,\"source\":\"STRAVA\","
                                        + "\"externalId\":\"strava-note\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(activity.get("status").asText()).isEqualTo("MATCHED");
        assertThat(activity.get("matchedWorkoutId").asText()).isEqualTo(workoutId);
    }

    /**
     * Une séance déclarée non faite reste hors d'atteinte : une sortie du même jour ne doit pas
     * contredire l'athlète qui a dit ne pas l'avoir courue.
     */
    @Test
    void uneSeanceDeclareeNonFaiteNeSeLaissePasRapprocher() throws Exception {
        LocalDate day = quietDay();
        String workoutId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scheduledDate\":\"" + day
                                        + "\",\"type\":\"ENDURANCE\",\"title\":\"EF ratee\","
                                        + "\"targetDistanceM\":12000,\"targetDurationS\":3600}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(patch("/me/workouts/{w}/feedback", workoutId)
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MISSED\",\"missedReason\":\"HEALTH\"}"))
                .andExpect(status().isOk());

        JsonNode activity = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"activityDate\":\"" + day + "\",\"title\":\"Sortie\","
                                        + "\"distanceM\":12000,\"durationS\":3600,\"source\":\"STRAVA\","
                                        + "\"externalId\":\"strava-missed\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(activity.get("matchedWorkoutId").isNull()).isTrue();
    }

    /**
     * Deux sorties le même jour ne se disputent pas la même séance : la seconde ne doit pas
     * déloger la première. Cette exclusion tenait à ce que le rapprochement fasse sortir la
     * séance de {@code PLANNED} ; elle est désormais posée explicitement.
     */
    @Test
    void uneSecondeSortieNeVolePasLaSeanceDeLaPremiere() throws Exception {
        LocalDate day = quietDay();
        String workoutId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scheduledDate\":\"" + day
                                        + "\",\"type\":\"ENDURANCE\",\"title\":\"EF\","
                                        + "\"targetDistanceM\":12000,\"targetDurationS\":3600}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        JsonNode first = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"activityDate\":\"" + day + "\",\"title\":\"Sortie 1\","
                                        + "\"distanceM\":12000,\"durationS\":3600,\"source\":\"STRAVA\","
                                        + "\"externalId\":\"strava-a\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(first.get("matchedWorkoutId").asText()).isEqualTo(workoutId);

        JsonNode second = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"activityDate\":\"" + day + "\",\"title\":\"Sortie 2\","
                                        + "\"distanceM\":12000,\"durationS\":3600,\"source\":\"STRAVA\","
                                        + "\"externalId\":\"strava-b\",\"confirmDuplicate\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(second.get("matchedWorkoutId").isNull()).isTrue();
    }

    /**
     * Un jour que le programme de démonstration ne couvre pas.
     *
     * <p>Ces deux scénarios veulent vérifier qu'<b>aucune</b> séance ne se laisse rapprocher. Or
     * la graine de démonstration pose un programme complet autour d'aujourd'hui : la séance
     * qu'elle place le jour même captait la sortie du test, et l'assertion tombait sans que rien
     * de ce qu'elle protège ne soit en cause. Le résultat dépendait du jour de la semaine — donc
     * de la date à laquelle la suite tournait, ce qui est la pire forme d'échec : celle qui fait
     * douter du code plutôt que du test.</p>
     */
    private LocalDate quietDay() {
        return LocalDate.now().minusDays(40);
    }

    private double acuteLoad() throws Exception {
        return objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/load", clubId, athleteId).header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("acuteLoad7d").asDouble();
    }
}
