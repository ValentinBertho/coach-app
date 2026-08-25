package com.coachrun;

import com.coachrun.entity.enums.ProposalStatus;
import com.coachrun.repository.AthleteProposalRepository;
import com.coachrun.entity.Workout;
import com.coachrun.repository.WorkoutRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le feu vert du matin, de bout en bout — et surtout <b>l'invariant qui le gouverne</b> :
 * l'application ne modifie jamais une séance toute seule.
 *
 * <p>Ce test vaut plus que la somme de ses assertions : il vérifie qu'il n'existe pas de chemin
 * par lequel une fatigue déclarée réécrit un entraînement. L'athlète demande, le coach décide, et
 * seule l'acceptation touche au calendrier. C'est la règle sur laquelle repose la confiance d'un
 * coach dans son propre plan.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReadinessProposalTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AthleteProposalRepository proposalRepository;
    @Autowired private WorkoutRepository workoutRepository;
    @Autowired private com.coachrun.service.ClockService clock;

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
    }

    @Test
    void aFreshAthleteGetsNoAdvice() throws Exception {
        // Un footing, posé explicitement. Le test lisait la séance que le jeu de démonstration
        // place ce jour-là — donc, un jour sur deux, un fractionné. Or la charge déclenche un
        // allègement SANS fatigue déclarée dès que la séance du jour est intense : le test
        // passait ou échouait selon la date d'exécution, sans que rien n'ait changé dans le
        // produit. Ce qu'il veut éprouver — rien de déclaré, donc rien à conseiller — n'a pas
        // besoin d'une séance intense.
        seedEasyTodaySession();

        JsonNode r = readiness();
        // Sans fatigue ni douleur déclarées, il n'y a rien à conseiller — et surtout rien à
        // proposer. Le silence est la réponse par défaut.
        assertThat(r.get("advice").asText()).isEqualTo("KEEP");
        assertThat(r.get("pendingRequest").asBoolean()).isFalse();
    }

    @Test
    void highFatigueAdvisesLighteningWithoutTouchingAnything() throws Exception {
        seedTodaySession();
        checkIn(8, 0);

        JsonNode r = readiness();
        assertThat(r.get("advice").asText()).isIn("LIGHTEN", "MOVE");
        assertThat(r.get("reason").asText()).contains("8");

        // L'essentiel : le conseil est une lecture. Rien n'a été écrit.
        assertThat(proposalRepository.findByAthleteIdAndStatusOrderByCreatedAtDesc(
                UUID.fromString(athleteId), ProposalStatus.PENDING)).isEmpty();
    }

    @Test
    void painRemovesIntensityRatherThanVolume() throws Exception {
        seedTodaySession();
        checkIn(2, 5);

        assertThat(readiness().get("advice").asText()).isIn("EASY", "MOVE");
    }

    /**
     * Le parcours complet : l'athlète demande, le coach accepte, et c'est <b>seulement là</b> que
     * la séance change.
     */
    @Test
    void theAthleteAsksAndOnlyTheCoachApplies() throws Exception {
        UUID workoutId = seedTodaySession();
        checkIn(8, 0);

        // Le conseil porte bien sur la séance du test, et il conseille d'alléger : sans ces deux
        // vérifications, la suite mesurerait l'effet d'une proposition dont on ignore la nature.
        JsonNode advice = readiness();
        assertThat(advice.get("workoutId").asText()).isEqualTo(workoutId.toString());
        assertThat(advice.get("advice").asText()).isEqualTo("LIGHTEN");
        // On n'affirme pas le libellé exact : MockMvc décode le corps en ISO-8859-1, et le « × »
        // en ressort sur deux caractères. C'est le volume annoncé qui compte, et il est vérifié
        // pour de bon plus bas, sur la structure écrite en base.
        assertThat(advice.get("adaptedSummary").asText()).startsWith("4 ");

        JsonNode requested = objectMapper.readTree(mvc.perform(post("/me/readiness/request")
                        .header("Authorization", athleteBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(requested.get("pendingRequest").asBoolean()).isTrue();

        // Une demande, pas une modification : la séance est intacte.
        var before = workoutRepository.findById(workoutId).orElseThrow();
        String snapshotBefore = before.getSessionSnapshot();

        JsonNode queue = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/proposals", clubId).header("Authorization", coachBearer)
                                .param("scope", "all"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).get("requestedByAthlete").asBoolean()).isTrue();
        assertThat(queue.get(0).get("type").asText()).isEqualTo("SESSION_LIGHTEN");
        assertThat(queue.get(0).get("targetId").asText()).isEqualTo(workoutId.toString());

        String proposalId = queue.get(0).get("id").asText();
        mvc.perform(post("/clubs/{c}/proposals/{p}/accept", clubId, proposalId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isOk());

        // Après acceptation seulement, la prescription a changé — et elle a changé dans le bon
        // sens : moins de répétitions, la séance reste une séance.
        var after = workoutRepository.findById(workoutId).orElseThrow();
        assertThat(after.getSessionSnapshot()).isNotEqualTo(snapshotBefore);
        assertThat(after.getSessionSnapshot()).contains("\"reps\":4");
    }

    @Test
    void aRefusedProposalNeverComesBack() throws Exception {
        seedTodaySession();
        checkIn(8, 0);
        mvc.perform(post("/me/readiness/request").header("Authorization", athleteBearer))
                .andExpect(status().isOk());

        JsonNode queue = proposals();
        String proposalId = queue.get(0).get("id").asText();
        mvc.perform(post("/clubs/{c}/proposals/{p}/dismiss", clubId, proposalId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isOk());

        assertThat(proposals()).isEmpty();

        // Redemander ne recrée rien : le refus vaut décision, sinon il ne veut rien dire.
        mvc.perform(post("/me/readiness/request").header("Authorization", athleteBearer))
                .andExpect(status().isOk());
        assertThat(proposals()).isEmpty();
    }

    @Test
    void aProposalCannotBeDecidedTwice() throws Exception {
        seedTodaySession();
        checkIn(8, 0);
        mvc.perform(post("/me/readiness/request").header("Authorization", athleteBearer))
                .andExpect(status().isOk());

        String proposalId = proposals().get(0).get("id").asText();
        mvc.perform(post("/clubs/{c}/proposals/{p}/accept", clubId, proposalId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isOk());
        mvc.perform(post("/clubs/{c}/proposals/{p}/accept", clubId, proposalId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isConflict());
    }

    /**
     * Un athlète ne demande que ce qui lui est conseillé : la route n'est pas un libre-service.
     *
     * <p>La séance est volontairement facile. Sur une séance intense, une charge élevée suffit à
     * justifier un allègement même sans fatigue déclarée — c'est le comportement voulu, et le test
     * porterait alors sur autre chose que son titre.</p>
     */
    @Test
    void anUnjustifiedRequestIsRefused() throws Exception {
        seedEasyTodaySession();
        checkIn(1, 0);

        assertThat(readiness().get("advice").asText()).isEqualTo("KEEP");
        mvc.perform(post("/me/readiness/request").header("Authorization", athleteBearer))
                .andExpect(status().isConflict());
    }

    // --- Fixtures -------------------------------------------------------------

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void checkIn(int fatigue, int pain) throws Exception {
        mvc.perform(post("/me/checkin").header("Authorization", athleteBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sleep\":5,\"fatigue\":" + fatigue + ",\"pain\":" + pain + "}"))
                .andExpect(status().isOk());
    }

    private JsonNode readiness() throws Exception {
        return objectMapper.readTree(mvc.perform(get("/me/readiness")
                        .header("Authorization", athleteBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode proposals() throws Exception {
        return objectMapper.readTree(mvc.perform(get("/clubs/{c}/proposals", clubId)
                        .header("Authorization", coachBearer).param("scope", "all"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /**
     * Une séance de fractionné aujourd'hui, avec une structure — sans elle, il n'y a rien à
     * alléger et le parcours ne prouverait rien.
     *
     * <p>Les séances du jour déjà posées par le jeu de démonstration sont retirées d'abord. Sans
     * ça, le conseil du matin porte sur la première séance de la journée — celle du jeu de démo,
     * qui n'a pas de structure — et le test mesurerait autre chose que ce qu'il annonce. C'est
     * aussi un rappel utile : sur une double séance, le feu vert s'adresse à la première.</p>
     */
    /** Un footing aujourd'hui : rien dans cette séance ne peut justifier une adaptation. */
    private UUID seedEasyTodaySession() throws Exception {
        return seedTodaySession("ENDURANCE", 3);
    }

    private UUID seedTodaySession() throws Exception {
        return seedTodaySession("INTERVALS", 8);
    }

    private UUID seedTodaySession(String type, int blockRpe) throws Exception {
        for (Workout existing : workoutRepository
                .findByAthleteIdAndScheduledDateOrderByCreatedAtAsc(UUID.fromString(athleteId), clock.today())) {
            workoutRepository.delete(existing);
        }
        workoutRepository.flush();

        String body = """
                {"scheduledDate":"%s","type":"%s","title":"6 x 1000",
                 "targetDistanceM":12000,"steps":[]}
                """.formatted(clock.today(), type);
        JsonNode created = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", coachBearer)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID workoutId = UUID.fromString(created.get("id").asText());

        String structure = """
                {"warmup":[{"id":"w1","type":"easy","reps":1,"durationS":900,"rpe":3}],
                 "main":[{"id":"m1","type":"intervals","reps":6,"distanceM":1000,"rpe":%d}],
                 "cooldown":[{"id":"c1","type":"easy","reps":1,"durationS":600,"rpe":3}]}
                """.formatted(blockRpe);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/clubs/{c}/athletes/{a}/workouts/{w}/structure", clubId, athleteId, workoutId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(structure))
                .andExpect(status().isOk());
        return workoutId;
    }
}
