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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un effort, un ressenti.
 *
 * <p>Une sortie rapprochée et sa séance décrivent la même heure de course. Elles portaient
 * pourtant chacune leur ressenti, saisissable depuis deux écrans différents : l'athlète pouvait
 * noter 4/10 sur l'une et 8/10 sur l'autre, et rien ne disait au coach laquelle croire. Ces tests
 * fixent le porteur unique — la <b>séance</b> — et les deux moments où la propriété change de
 * main, le rapprochement et le détachement, qui sont exactement ceux où l'on risque de perdre ce
 * que l'athlète a écrit.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SingleDebriefTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private com.coachrun.repository.UserRepository userRepository;
    @Autowired private com.coachrun.service.ClockService clock;

    private MockMvc mvc;
    private String coach;
    private String athlete;
    private String clubId;
    private String athleteId;
    private LocalDate day;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        coach = bearer(DemoSeedService.HEAD_COACH_EMAIL);
        athlete = bearer(DemoSeedService.ATHLETE_EMAIL);

        User athleteUser = userRepository.findByEmailIgnoreCase(DemoSeedService.ATHLETE_EMAIL).orElseThrow();
        athleteId = athleteUser.getAthlete().getId().toString();
        clubId = athleteUser.getClub().getId().toString();
        // Un jour calme, hors du programme posé par le jeu de démonstration.
        day = clock.today().minusDays(45);
    }

    private String bearer(String email) throws Exception {
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return "Bearer " + auth.get("accessToken").asText();
    }

    /** Une séance prescrite ce jour-là, 10 km. */
    private String plannedWorkout() throws Exception {
        return objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", coach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"" + day + "\",\"type\":\"ENDURANCE\","
                                + "\"title\":\"Footing\",\"targetDistanceM\":10000,\"targetDurationS\":3300}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    /** Une sortie du même jour, saisie par l'athlète (rapprochée si une séance l'attend). */
    private String loggedActivity(int distanceM) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/me/activities")
                        .header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityDate\":\"" + day + "\",\"title\":\"Sortie\","
                                + "\"distanceM\":" + distanceM + ",\"durationS\":3300}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    private JsonNode activity(String activityId) throws Exception {
        JsonNode all = objectMapper.readTree(mvc.perform(get("/me/activities")
                        .header("Authorization", athlete))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode a : all) {
            if (a.get("id").asText().equals(activityId)) {
                return a;
            }
        }
        throw new AssertionError("Sortie introuvable : " + activityId);
    }

    /**
     * Le cœur : noter sa sortie rapprochée revient à noter sa séance. Les deux écrans lisent la
     * même chose parce qu'il n'y a qu'une chose à lire.
     */
    @Test
    void leRessentiDUneSortieRapprocheeEstCeluiDeSaSeance() throws Exception {
        String workoutId = plannedWorkout();
        String activityId = loggedActivity(10200);

        mvc.perform(patch("/me/activities/{id}", activityId)
                        .header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rpe\":7,\"feel\":2,\"comment\":\"jambes lourdes\"}"))
                .andExpect(status().isOk());

        // La séance porte le ressenti…
        mvc.perform(get("/me/workouts/{id}", workoutId).header("Authorization", athlete))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rpe").value(7))
                .andExpect(jsonPath("$.feel").value(2))
                .andExpect(jsonPath("$.athleteComment").value("jambes lourdes"));

        // …et la sortie rend exactement le même, sans en garder de copie propre.
        JsonNode a = activity(activityId);
        org.assertj.core.api.Assertions.assertThat(a.get("rpe").asInt()).isEqualTo(7);
        org.assertj.core.api.Assertions.assertThat(a.get("feel").asInt()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(a.get("athleteComment").asText())
                .isEqualTo("jambes lourdes");
    }

    /** Et dans l'autre sens : le débrief posé sur la séance se lit depuis la sortie. */
    @Test
    void leRessentiDeLaSeanceSeLitDepuisLaSortie() throws Exception {
        String workoutId = plannedWorkout();
        String activityId = loggedActivity(10200);

        mvc.perform(patch("/me/workouts/{id}/feedback", workoutId)
                        .header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"rpe\":6,\"feel\":1,\"comment\":\"nickel\"}"))
                .andExpect(status().isOk());

        JsonNode a = activity(activityId);
        org.assertj.core.api.Assertions.assertThat(a.get("rpe").asInt()).isEqualTo(6);
        org.assertj.core.api.Assertions.assertThat(a.get("athleteComment").asText()).isEqualTo("nickel");
    }

    /**
     * L'ordre habituel quand la montre se synchronise après coup : on débriefe une sortie libre,
     * elle est rapprochée ensuite. Le ressenti doit suivre — sinon il disparaît de l'écran au
     * moment même où le rapprochement est censé enrichir la lecture.
     */
    @Test
    void leRessentiDUneSortieLibreSuitLeRapprochement() throws Exception {
        String activityId = loggedActivity(10200);

        mvc.perform(patch("/me/activities/{id}", activityId)
                        .header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rpe\":8,\"comment\":\"dur\"}"))
                .andExpect(status().isOk());

        // La séance n'arrive qu'après, et le coach la rattache.
        String workoutId = plannedWorkout();
        mvc.perform(patch("/me/activities/{id}/match", activityId)
                        .header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workoutId\":\"" + workoutId + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/me/workouts/{id}", workoutId).header("Authorization", athlete))
                .andExpect(jsonPath("$.rpe").value(8))
                .andExpect(jsonPath("$.athleteComment").value("dur"));
        org.assertj.core.api.Assertions.assertThat(activity(activityId).get("rpe").asInt()).isEqualTo(8);
    }

    /**
     * Détachement : la sortie récupère le ressenti de la séance qu'elle quitte. Il décrit l'effort
     * qu'elle porte, et défaire un rapprochement ne doit pas le reprendre à l'athlète.
     */
    @Test
    void leRessentiResteALaSortieQuandOnLaDetache() throws Exception {
        String workoutId = plannedWorkout();
        String activityId = loggedActivity(10200);

        mvc.perform(patch("/me/workouts/{id}/feedback", workoutId)
                        .header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"rpe\":5,\"comment\":\"ok\"}"))
                .andExpect(status().isOk());

        mvc.perform(patch("/me/activities/{id}/match", activityId)
                        .header("Authorization", athlete))
                .andExpect(status().isOk());

        JsonNode a = activity(activityId);
        org.assertj.core.api.Assertions.assertThat(a.get("matchedWorkoutId").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(a.get("rpe").asInt()).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(a.get("athleteComment").asText()).isEqualTo("ok");
    }

    /** Une sortie libre garde le sien : c'est elle qui le porte, il n'y a personne d'autre. */
    @Test
    void uneSortieLibreGardeSonPropreRessenti() throws Exception {
        String activityId = loggedActivity(8000);

        mvc.perform(patch("/me/activities/{id}", activityId)
                        .header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rpe\":4,\"feel\":1,\"fatigue\":0,\"pain\":0,\"comment\":\"tranquille\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rpe").value(4))
                .andExpect(jsonPath("$.feel").value(1))
                // 0 est une valeur — « aucune » — et non une absence de réponse.
                .andExpect(jsonPath("$.fatigue").value(0))
                .andExpect(jsonPath("$.pain").value(0));
    }
}
