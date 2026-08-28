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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'ordre des séances d'une même journée.
 *
 * <p>Un coach qui pose deux séances le même jour — footing le matin, séance de côtes le soir —
 * n'avait aucun moyen de dire laquelle vient d'abord. Il pouvait les réordonner en les glissant,
 * mais l'ordre s'arrêtait à son écran : le calendrier de l'athlète triait par date seule, donc par
 * ordre de création.</p>
 *
 * <p>Ces tests tiennent les deux moitiés du besoin : l'ordre voulu <b>arrive</b> jusqu'à l'athlète,
 * et il reste <b>facultatif</b> — une journée où le coach n'a rien demandé ne doit porter aucune
 * consigne d'ordre, sans quoi l'athlète lirait une prescription que personne n'a écrite.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkoutDayOrderTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String coachBearer;
    private String athleteBearer;
    private String clubId;
    private String athleteId;

    private static final LocalDate JOUR = LocalDate.now().plusDays(9);

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

    /** Deux séances posées le même jour ne portent aucun ordre : le coach n'a rien demandé. */
    @Test
    void aDayWithoutADecisionCarriesNoOrder() throws Exception {
        createWorkout("Footing matin");
        createWorkout("Cotes soir");

        assertThat(orderIndexesOf(athleteDay()))
                .as("toutes a zero : aucune consigne d'ordre")
                .containsOnly(0);
    }

    /**
     * L'ordre voulu par le coach arrive jusqu'à l'athlète.
     *
     * <p>C'est le cœur du besoin : sans cela, le glisser-déposer du coach ne changeait rien à ce
     * que l'athlète voyait.</p>
     */
    @Test
    void theCoachOrderReachesTheAthlete() throws Exception {
        String matin = createWorkout("Footing matin").get("id").asText();
        String soir = createWorkout("Cotes soir").get("id").asText();

        // Le coach veut les côtes d'abord.
        reorder(List.of(soir, matin));

        assertThat(titlesOf(athleteDay()))
                .as("l'athlete doit lire la journee dans l'ordre voulu par son coach")
                .containsExactly("Cotes soir", "Footing matin");
        assertThat(orderIndexesOf(athleteDay())).containsExactly(0, 1);
    }

    /** Et le coach peut le retirer : l'ordre reste facultatif jusqu'au bout. */
    @Test
    void theCoachCanTakeTheOrderBack() throws Exception {
        String matin = createWorkout("Footing matin").get("id").asText();
        String soir = createWorkout("Cotes soir").get("id").asText();
        reorder(List.of(soir, matin));

        mvc.perform(delete("/clubs/{c}/athletes/{a}/workouts/order", clubId, athleteId)
                        .header("Authorization", coachBearer)
                        .param("date", JOUR.toString()))
                .andExpect(status().isNoContent());

        assertThat(orderIndexesOf(athleteDay()))
                .as("l'ordre retire, la journee redevient libre")
                .containsOnly(0);
    }

    /**
     * Une séance ajoutée à une journée ordonnée se range à la suite.
     *
     * <p>C'est l'invariant fragile de la convention retenue : sans cela, la nouvelle séance
     * arriverait à zéro et se retrouverait <em>en tête</em> d'un ordre qu'elle n'a pas choisi.</p>
     */
    @Test
    void aWorkoutAddedToAnOrderedDayGoesLast() throws Exception {
        String matin = createWorkout("Footing matin").get("id").asText();
        String soir = createWorkout("Cotes soir").get("id").asText();
        reorder(List.of(soir, matin));

        createWorkout("Gainage");

        assertThat(titlesOf(athleteDay()))
                .as("la nouvelle venue se court en dernier, faute d'indication contraire")
                .containsExactly("Cotes soir", "Footing matin", "Gainage");
    }

    /**
     * Une séance qui quitte une journée ordonnée n'emporte pas son rang.
     *
     * <p>Sinon une journée vide se serait mise à afficher un ordre toute seule, parce qu'elle
     * accueillait une séance qui portait un 1.</p>
     */
    @Test
    void aWorkoutLeavingAnOrderedDayDropsItsRank() throws Exception {
        String matin = createWorkout("Footing matin").get("id").asText();
        String soir = createWorkout("Cotes soir").get("id").asText();
        reorder(List.of(soir, matin));

        // « Footing matin » porte le rang 1 ; on le déplace sur une journée vierge.
        LocalDate ailleurs = JOUR.plusDays(1);
        mvc.perform(patch("/clubs/{c}/athletes/{a}/workouts/{w}/reschedule", clubId, athleteId, matin)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"" + ailleurs + "\"}"))
                .andExpect(status().isOk());

        assertThat(orderIndexesOf(athleteDayOn(ailleurs)))
                .as("une journee d'accueil ne doit pas heriter d'un ordre")
                .containsOnly(0);
    }

    /**
     * L'écran « Aujourd'hui » aussi : c'est là qu'une double séance se lit le matin même, et il
     * triait par date de création — donc dans l'ordre où le coach avait tapé les séances, qui n'a
     * aucune raison d'être celui où il veut les voir courues.
     */
    @Test
    void todayScreenFollowsTheCoachOrder() throws Exception {
        LocalDate aujourdhui = LocalDate.now();
        String matin = createWorkoutOn(aujourdhui, "Footing matin").get("id").asText();
        String soir = createWorkoutOn(aujourdhui, "Cotes soir").get("id").asText();

        mvc.perform(patch("/clubs/{c}/athletes/{a}/workouts/reorder", clubId, athleteId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "date", aujourdhui.toString(), "orderedIds", List.of(soir, matin)))))
                .andExpect(status().isNoContent());

        JsonNode today = objectMapper.readTree(mvc.perform(get("/me/today")
                        .header("Authorization", athleteBearer)
                        .param("date", aujourdhui.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(titlesOf(today))
                .as("la journee du jour se lit dans l'ordre voulu par le coach")
                .containsExactly("Cotes soir", "Footing matin");
    }

    // --- Utilitaires ---------------------------------------------------------------------------

    private JsonNode createWorkout(String title) throws Exception {
        return createWorkoutOn(JOUR, title);
    }

    private JsonNode createWorkoutOn(LocalDate date, String title) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduledDate", date.toString(),
                                "type", "ENDURANCE",
                                "title", title))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void reorder(List<String> orderedIds) throws Exception {
        mvc.perform(patch("/clubs/{c}/athletes/{a}/workouts/reorder", clubId, athleteId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "date", JOUR.toString(), "orderedIds", orderedIds))))
                .andExpect(status().isNoContent());
    }

    /** La journée telle que l'ATHLÈTE la lit — c'est là que l'ordre doit se voir. */
    private JsonNode athleteDay() throws Exception {
        return athleteDayOn(JOUR);
    }

    private JsonNode athleteDayOn(LocalDate date) throws Exception {
        return objectMapper.readTree(mvc.perform(get("/me/workouts")
                        .header("Authorization", athleteBearer)
                        .param("from", date.toString())
                        .param("to", date.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private List<String> titlesOf(JsonNode day) {
        return java.util.stream.StreamSupport.stream(day.spliterator(), false)
                .map(w -> w.get("title").asText()).toList();
    }

    private List<Integer> orderIndexesOf(JsonNode day) {
        return java.util.stream.StreamSupport.stream(day.spliterator(), false)
                .map(w -> w.get("orderIndex").asInt()).toList();
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
