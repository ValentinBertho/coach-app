package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.User;
import com.coachrun.entity.Activity;
import com.coachrun.entity.AthletePerformance;
import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.Workout;
import com.coachrun.entity.WorkoutStep;
import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.ActivityStatus;
import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.entity.enums.RunDistance;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.UserRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Changer de coach sans passer par « quitter, puis chercher » : l'athlète confirme le changement,
 * et son suivi actuel s'arrête au moment où le nouveau commence.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachSwitchTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CoachAthleteRelationRepository relationRepository;
    @Autowired private com.coachrun.repository.AthleteRepository athleteRepository;
    @Autowired private com.coachrun.repository.ClubRepository clubRepository;
    @Autowired private com.coachrun.repository.AthletePerformanceRepository performanceRepository;
    @Autowired private com.coachrun.repository.DeviceConnectionRepository connectionRepository;
    @Autowired private com.coachrun.repository.WorkoutRepository workoutRepository;
    @Autowired private com.coachrun.repository.ActivityRepository activityRepository;
    @Autowired private com.coachrun.service.CoachingDataRecoveryService recoveryService;

    private MockMvc mvc;
    private String clubCoach;
    private String soloCoach;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        demoSeedService.seedHub();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        clubCoach = login(DemoSeedService.HEAD_COACH_EMAIL, DemoSeedService.DEMO_PASSWORD);
        soloCoach = login(DemoSeedService.SOLO_COACH_EMAIL, DemoSeedService.DEMO_PASSWORD);
    }

    /** Sans confirmation, refus clair ; avec, la demande part, et l'acceptation fait le changement. */
    @Test
    void aConfirmedRequestSwitchesCoachOnAcceptance() throws Exception {
        String email = "lea-" + UUID.randomUUID() + "@exemple.fr";
        String athlete = registerAthlete(email);
        join(athlete, link(clubCoach, "{\"directEntry\":true}"), "{}").andExpect(status().isOk());
        UUID oldFiche = fiche(email).getId();

        String publicLink = link(soloCoach, "{\"label\":\"Changement\"}");
        athlete = login(email, "password1234");
        String refused = join(athlete, publicLink, "{}").andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(refused).contains("changement de coach");

        JsonNode res = json(join(athlete, publicLink, "{\"switchCoach\":true}").andExpect(status().isOk()));
        assertThat(res.get("outcome").asText()).isEqualTo("REQUESTED");
        assertThat(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(oldFiche))
                .as("rien ne change tant que le nouveau coach n'a pas répondu").isPresent();

        String requestId = null;
        for (JsonNode r : json(mvc.perform(get("/me/received-requests").header("Authorization", soloCoach)))) {
            if (r.toString().contains("Changement")) {
                requestId = r.get("id").asText();
            }
        }
        mvc.perform(post("/me/received-requests/{id}/accept", requestId).header("Authorization", soloCoach))
                .andExpect(status().isOk());

        assertThat(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(oldFiche))
                .as("l'ancien suivi est clos").isEmpty();
        Athlete now = fiche(email);
        assertThat(now.getId()).isNotEqualTo(oldFiche);
        assertThat(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(now.getId()).orElseThrow()
                .getCoach().getEmail()).isEqualToIgnoringCase(DemoSeedService.SOLO_COACH_EMAIL);
    }

    /** Lien à entrée directe : le changement se fait d'un coup. Revenir retrouve l'ancienne fiche. */
    @Test
    void aDirectLinkSwitchesAtOnceAndComingBackReusesTheFiche() throws Exception {
        String email = "tom-" + UUID.randomUUID() + "@exemple.fr";
        String athlete = registerAthlete(email);
        join(athlete, link(clubCoach, "{\"directEntry\":true}"), "{}").andExpect(status().isOk());
        UUID clubFiche = fiche(email).getId();

        athlete = login(email, "password1234");
        JsonNode res = json(join(athlete, link(soloCoach, "{\"directEntry\":true}"),
                "{\"switchCoach\":true,\"shareHistory\":false}").andExpect(status().isOk()));
        assertThat(res.get("outcome").asText()).isEqualTo("JOINED");
        assertThat(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(clubFiche)).isEmpty();

        athlete = login(email, "password1234");
        join(athlete, link(clubCoach, "{\"directEntry\":true}"), "{\"switchCoach\":true}").andExpect(status().isOk());
        assertThat(fiche(email).getId()).as("il retrouve sa fiche du club").isEqualTo(clubFiche);
        assertThat(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(clubFiche)).isPresent();

        // L'écran du coach ne montre ce retour qu'une fois en « Suivi », l'ancien passe en historique.
        int ongoing = 0;
        int ended = 0;
        for (JsonNode r : json(mvc.perform(get("/me/received-requests").header("Authorization", clubCoach)))) {
            if (clubFiche.toString().equals(r.path("createdAthleteId").asText())) {
                if (r.path("coachingActive").asBoolean()) {
                    ongoing++;
                } else {
                    ended++;
                }
            }
        }
        assertThat(ongoing).as("un seul suivi en cours").isEqualTo(1);
        assertThat(athleteRepository.searchAdmin(null, null, email,
                        org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .as("le back-office ne double pas l'athlète avec son espace personnel")
                .isNotEmpty().noneMatch(a -> clubRepository.findById(a.getClub().getId()).orElseThrow().isPersonal());
        assertThat(ongoing + ended).as("chaque arrivée a laissé une demande").isGreaterThanOrEqualTo(1);
    }

    /**
     * Changer de coach ne fait plus repartir de zéro : montres, records et seuils suivent
     * toujours ; le calendrier passé reste à l'athlète et n'est transmis que s'il l'a choisi.
     */
    @Test
    void profileAndDevicesFollowAndPastCalendarStaysWithTheAthlete() throws Exception {
        String email = "zoe-" + UUID.randomUUID() + "@exemple.fr";
        String athlete = registerAthlete(email);
        join(athlete, link(clubCoach, "{\"directEntry\":true}"), "{}").andExpect(status().isOk());
        Athlete old = seedCoachingData(fiche(email));

        athlete = login(email, "password1234");
        join(athlete, link(soloCoach, "{\"directEntry\":true}"),
                "{\"switchCoach\":true,\"shareHistory\":false}").andExpect(status().isOk());
        Athlete now = fiche(email);
        assertThat(now.getId()).isNotEqualTo(old.getId());

        assertThat(now.getHrMax()).isEqualTo(192);
        assertThat(now.getVma()).isEqualByComparingTo("17.5");
        assertThat(now.getLt2Ms()).isEqualByComparingTo("4.20");
        assertThat(performanceRepository.findByAthleteIdOrderByDateSetDescCreatedAtDesc(now.getId()))
                .as("ses records le suivent").hasSize(1);
        assertThat(now.getVdot()).as("le VDOT est recalculé chez le nouveau coach").isNotNull();
        assertThat(connectionRepository.findByAthleteId(now.getId()))
                .as("sa montre le suit").extracting(DeviceConnection::getProvider)
                .containsExactly(DeviceProvider.STRAVA);
        assertThat(workoutRepository.findByAthleteIdOrderByScheduledDateAsc(now.getId()))
                .as("pas de calendrier transmis sans son accord").isEmpty();

        Athlete personal = athleteRepository.findFirstByAccountIdAndClubPersonalTrue(now.getAccount().getId())
                .orElseThrow();
        assertThat(workoutRepository.findByAthleteIdOrderByScheduledDateAsc(personal.getId()))
                .as("il garde son calendrier passé dans son espace").hasSize(1);
        assertThat(workoutRepository.findByAthleteIdOrderByScheduledDateAsc(old.getId()))
                .as("l'ancien coach garde son suivi").hasSize(1);
    }

    /** Case cochée : le nouveau coach voit le calendrier passé, sortie rapprochée comprise. */
    @Test
    void sharedHistoryBringsPastCalendarToTheNewCoach() throws Exception {
        String email = "max-" + UUID.randomUUID() + "@exemple.fr";
        String athlete = registerAthlete(email);
        join(athlete, link(clubCoach, "{\"directEntry\":true}"), "{}").andExpect(status().isOk());
        seedCoachingData(fiche(email));

        athlete = login(email, "password1234");
        join(athlete, link(soloCoach, "{\"directEntry\":true}"),
                "{\"switchCoach\":true,\"shareHistory\":true}").andExpect(status().isOk());
        Athlete now = fiche(email);
        var workouts = workoutRepository.findByAthleteIdOrderByScheduledDateAsc(now.getId());
        assertThat(workouts).hasSize(1);
        assertThat(workouts.get(0).getRpe()).isEqualTo(6);
        assertThat(workouts.get(0).getSteps()).hasSize(1);
        var activities = activityRepository.findByAthleteIdOrderByActivityDateDesc(now.getId());
        assertThat(activities).hasSize(1);
        assertThat(activities.get(0).getMatchedWorkoutId()).isEqualTo(workouts.get(0).getId());
    }

    /**
     * Rattrapage d'un changement fait avant la correction : la fiche actuelle était née vierge.
     * L'admin ramène profil, montre et (si demandé) calendrier passé, sans rien retirer à l'ancien.
     */
    @Test
    void adminRecoveryBringsBackDataLeftOnAFormerFiche() throws Exception {
        String email = "eva-" + UUID.randomUUID() + "@exemple.fr";
        String athlete = registerAthlete(email);
        join(athlete, link(clubCoach, "{\"directEntry\":true}"), "{}").andExpect(status().isOk());
        Athlete old = seedCoachingData(fiche(email));

        // L'état laissé par l'ancien changement de coach : une fiche vierge chez le nouveau coach.
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        User solo = userRepository.findByEmailIgnoreCase(DemoSeedService.SOLO_COACH_EMAIL).orElseThrow();
        Athlete blank = new Athlete();
        blank.setClub(solo.getClub());
        blank.setAccount(old.getAccount());
        blank.setFirstName("Eva");
        blank.setLastName("Martin");
        blank.setHrMax(185);
        blank = athleteRepository.save(blank);
        user.setAthlete(blank);
        user.setClub(solo.getClub());
        userRepository.saveAndFlush(user);

        var result = recoveryService.recover(user.getId(), true);
        assertThat(result.formerFiches()).isEqualTo(1);
        Athlete now = athleteRepository.findById(blank.getId()).orElseThrow();
        assertThat(now.getHrMax()).as("ce que le nouveau coach a saisi n'est pas écrasé").isEqualTo(185);
        assertThat(now.getVma()).isEqualByComparingTo("17.5");
        assertThat(performanceRepository.findByAthleteIdOrderByDateSetDescCreatedAtDesc(now.getId())).hasSize(1);
        assertThat(connectionRepository.findByAthleteId(now.getId())).hasSize(1);
        assertThat(workoutRepository.findByAthleteIdOrderByScheduledDateAsc(now.getId())).hasSize(1);
        assertThat(workoutRepository.findByAthleteIdOrderByScheduledDateAsc(old.getId()))
                .as("l'ancien coach garde son suivi").hasSize(1);

        // Rejouer ne duplique rien.
        recoveryService.recover(user.getId(), true);
        assertThat(workoutRepository.findByAthleteIdOrderByScheduledDateAsc(now.getId())).hasSize(1);
        assertThat(performanceRepository.findByAthleteIdOrderByDateSetDescCreatedAtDesc(now.getId())).hasSize(1);
    }

    private Athlete seedCoachingData(Athlete fiche) {
        fiche.setHrMax(192);
        fiche.setVma(new java.math.BigDecimal("17.5"));
        fiche.setLt2Ms(new java.math.BigDecimal("4.20"));
        athleteRepository.save(fiche);

        AthletePerformance perf = new AthletePerformance();
        perf.setAthlete(fiche);
        perf.setDistance(RunDistance.D10KM);
        perf.setTimeSeconds(40 * 60);
        perf.setDateSet(java.time.LocalDate.now().minusMonths(1));
        performanceRepository.save(perf);

        DeviceConnection conn = new DeviceConnection();
        conn.setAthlete(fiche);
        conn.setProvider(DeviceProvider.STRAVA);
        conn.setAccessToken("a");
        conn.setRefreshToken("r");
        conn.setExpiresAt(0);
        connectionRepository.save(conn);

        Workout w = new Workout();
        w.setClub(fiche.getClub());
        w.setAthlete(fiche);
        w.setScheduledDate(java.time.LocalDate.now().minusDays(3));
        w.setType(WorkoutType.THRESHOLD);
        w.setStatus(WorkoutStatus.COMPLETED);
        w.setTitle("Seuil 3x10'");
        w.setRpe(6);
        WorkoutStep step = new WorkoutStep();
        step.setWorkout(w);
        step.setStepType(com.coachrun.entity.enums.WorkoutStepType.values()[0]);
        step.setDurationS(600);
        w.getSteps().add(step);
        w = workoutRepository.save(w);

        Activity a = new Activity();
        a.setClub(fiche.getClub());
        a.setAthlete(fiche);
        a.setSource(ActivitySource.STRAVA);
        a.setExternalId("ext-" + UUID.randomUUID());
        a.setActivityDate(w.getScheduledDate());
        a.setMatchedWorkoutId(w.getId());
        a.setStatus(ActivityStatus.MATCHED);
        activityRepository.save(a);
        workoutRepository.flush();
        return fiche;
    }

    /** Demander à rejoindre le coach qu'on a déjà n'a pas de sens. */
    @Test
    void switchingToTheSameCoachIsRefused() throws Exception {
        String email = "ines-" + UUID.randomUUID() + "@exemple.fr";
        String athlete = registerAthlete(email);
        join(athlete, link(clubCoach, "{\"directEntry\":true}"), "{}").andExpect(status().isOk());
        athlete = login(email, "password1234");
        join(athlete, link(clubCoach, "{}"), "{\"switchCoach\":true}").andExpect(status().isConflict());
    }

    private ResultActions join(String athlete, String token, String body) throws Exception {
        return mvc.perform(post("/me/invites/{t}/join", token).header("Authorization", athlete)
                .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8).content(body));
    }

    private String link(String coach, String body) throws Exception {
        return json(mvc.perform(post("/me/invite-links").header("Authorization", coach)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().isCreated())).get("token").asText();
    }

    private Athlete fiche(String email) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        return user.getAthlete();
    }

    private String registerAthlete(String email) throws Exception {
        json(mvc.perform(post("/public/athlete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1234\","
                                + "\"firstName\":\"Lea\",\"lastName\":\"Martin\","
                                + "\"birthDate\":\"" + java.time.LocalDate.now().minusYears(28) + "\","
                                + "\"termsAccepted\":true,\"healthDataConsent\":true}"))
                .andExpect(status().isCreated()));
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
        return login(email, "password1234");
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String login(String email, String password) throws Exception {
        return "Bearer " + MfaTestSupport.complete(mvc, context, email, json(mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()))).get("accessToken").asText();
    }
}
