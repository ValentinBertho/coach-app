package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.repository.AthleteAccountRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.DeviceConnectionRepository;
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
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'espace personnel : un athlète sans coach a sa fiche, son calendrier et sa montre.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersonalSpaceTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.coachrun.repository.UserRepository userRepository;
    @Autowired private AthleteRepository athleteRepository;
    @Autowired private AthleteAccountRepository accountRepository;
    @Autowired private CoachAthleteRelationRepository relationRepository;
    @Autowired private DeviceConnectionRepository connectionRepository;
    @Autowired private com.coachrun.repository.WorkoutTemplateRepository templateRepository;
    @Autowired private com.coachrun.repository.WorkoutRepository workoutRepository;

    private MockMvc mvc;
    private String coachBearer;
    private String adminBearer;
    private String athleteBearer;
    private String athleteEmail;
    private String slug;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        coachBearer = bearer(DemoSeedService.HEAD_COACH_EMAIL);
        adminBearer = bearer(DemoSeedService.ADMIN_EMAIL);
        slug = publishCoachProfile();
        athleteEmail = "perso-" + UUID.randomUUID() + "@exemple.fr";
        athleteBearer = registerAthlete(athleteEmail);
    }

    /** Dès l'inscription : une fiche personnelle, et le portail répond. */
    @Test
    void aNewAthleteHasHisOwnSpaceRightAway() throws Exception {
        JsonNode me = json(mvc.perform(get("/auth/me").header("Authorization", athleteBearer))
                .andExpect(status().isOk()));
        assertThat(me.get("athleteId").isNull()).isFalse();
        assertThat(me.get("personalSpace").asBoolean()).isTrue();

        mvc.perform(get("/me/workouts").header("Authorization", athleteBearer)
                        .param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isOk());
        mvc.perform(get("/me/races").header("Authorization", athleteBearer)).andExpect(status().isOk());
        mvc.perform(get("/me/strava").header("Authorization", athleteBearer)).andExpect(status().isOk());
    }

    /** L'espace personnel n'apparaît pas dans la liste des clubs du back-office. */
    @Test
    void personalSpacesStayOutOfTheAdminClubList() throws Exception {
        UUID clubId = currentAthlete().getClub().getId();
        String list = mvc.perform(get("/admin/clubs").param("size", "200").header("Authorization", adminBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(list).doesNotContain(clubId.toString());
    }

    /** Accepté par un coach : la fiche du coach prend le relais, et la montre suit. */
    @Test
    void theWatchFollowsTheAthleteToHisCoach() throws Exception {
        Athlete personal = currentAthlete();
        plugStrava(personal.getId());

        acceptCoachingRequest(sendRequest());

        Athlete coached = currentAthlete();
        assertThat(coached.getId()).isNotEqualTo(personal.getId());
        assertThat(coached.getClub().isPersonal()).isFalse();
        assertThat(connectionRepository.findByAthleteIdAndProvider(coached.getId(), DeviceProvider.STRAVA))
                .isPresent();
        assertThat(connectionRepository.findByAthleteIdAndProvider(personal.getId(), DeviceProvider.STRAVA))
                .isEmpty();
        JsonNode me = json(mvc.perform(get("/auth/me")
                .header("Authorization", bearer(athleteEmail, "password1234"))).andExpect(status().isOk()));
        assertThat(me.get("personalSpace").asBoolean()).isFalse();
    }

    /** Fin du coaching : retour dans son espace, la même fiche qu'avant. */
    @Test
    void endingTheCoachingBringsTheAthleteHome() throws Exception {
        UUID personalId = currentAthlete().getId();
        acceptCoachingRequest(sendRequest());

        mvc.perform(post("/me/coach/end").header("Authorization", bearer(athleteEmail, "password1234"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());

        assertThat(currentAthlete().getId()).isEqualTo(personalId);
        // Dans son espace, il n'a pas de coach à quitter.
        mvc.perform(post("/me/coach/end").header("Authorization", bearer(athleteEmail, "password1234"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    /** Une fiche personnelle ne compte pas comme un coach : il peut envoyer une demande. */
    @Test
    void aPersonalSpaceDoesNotBlockRequests() throws Exception {
        sendRequest();
    }

    /** Sans coach, l'athlète construit sa semaine : il ajoute une séance, et peut la retirer. */
    @Test
    void anAthleteAddsAndRemovesHisOwnWorkout() throws Exception {
        JsonNode created = json(mvc.perform(post("/me/own-workouts").header("Authorization", athleteBearer)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"scheduledDate\":\"2026-12-01\",\"type\":\"ENDURANCE\","
                                + "\"title\":\"Footing entre amis\",\"targetDurationS\":3600}"))
                .andExpect(status().isCreated()));
        assertThat(created.get("createdByAthlete").asBoolean()).isTrue();

        JsonNode week = json(mvc.perform(get("/me/workouts").header("Authorization", athleteBearer)
                .param("from", "2026-12-01").param("to", "2026-12-07")).andExpect(status().isOk()));
        assertThat(week.toString()).contains("Footing entre amis");

        mvc.perform(delete("/me/own-workouts/{id}", created.get("id").asText())
                        .header("Authorization", athleteBearer))
                .andExpect(status().isNoContent());
    }

    /** Le catalogue de son espace : une séance posée à une date, marquée « ajoutée par moi ». */
    @Test
    void anAthleteSchedulesAWorkoutFromHisCatalog() throws Exception {
        com.coachrun.entity.WorkoutTemplate t = new com.coachrun.entity.WorkoutTemplate();
        t.setClub(currentAthlete().getClub());
        t.setName("Sortie longue 1 h 30");
        t.setTitle("Sortie longue");
        t.setType(com.coachrun.entity.enums.WorkoutType.LONG_RUN);
        t.setTargetDurationS(5400);
        t = templateRepository.saveAndFlush(t);

        JsonNode catalog = json(mvc.perform(get("/me/catalog").header("Authorization", athleteBearer))
                .andExpect(status().isOk()));
        assertThat(catalog.toString()).contains("Sortie longue 1 h 30");

        JsonNode scheduled = json(mvc.perform(post("/me/catalog/{id}/schedule", t.getId())
                        .param("date", "2026-12-06").header("Authorization", athleteBearer))
                .andExpect(status().isCreated()));
        assertThat(scheduled.get("createdByAthlete").asBoolean()).isTrue();
        assertThat(scheduled.get("scheduledDate").asText()).isEqualTo("2026-12-06");
    }

    /**
     * Suivi par un coach : il peut toujours glisser ses propres séances, mais le catalogue est celui
     * du coach, et une séance du coach ne se supprime pas.
     */
    @Test
    void aCoachedAthleteKeepsOwnWorkoutsButNotTheCoachOnes() throws Exception {
        acceptCoachingRequest(sendRequest());
        String bearer = bearer(athleteEmail, "password1234");

        mvc.perform(get("/me/catalog").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(post("/me/own-workouts").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"scheduledDate\":\"2026-12-02\",\"type\":\"ENDURANCE\","
                                + "\"title\":\"Sortie du club\"}"))
                .andExpect(status().isCreated());

        Athlete coached = currentAthlete();
        com.coachrun.entity.Workout fromCoach = new com.coachrun.entity.Workout();
        fromCoach.setClub(coached.getClub());
        fromCoach.setAthlete(coached);
        fromCoach.setStatus(com.coachrun.entity.enums.WorkoutStatus.PLANNED);
        fromCoach.setScheduledDate(java.time.LocalDate.of(2026, 12, 3));
        fromCoach.setType(com.coachrun.entity.enums.WorkoutType.THRESHOLD);
        fromCoach.setTitle("Seuil");
        fromCoach = workoutRepository.saveAndFlush(fromCoach);

        mvc.perform(delete("/me/own-workouts/{id}", fromCoach.getId()).header("Authorization", bearer))
                .andExpect(status().isForbidden());
    }

    private Athlete currentAthlete() {
        return userRepository.findByEmailIgnoreCase(athleteEmail).orElseThrow().getAthlete();
    }


    private UUID currentAthleteId() {
        return userRepository.findByEmailIgnoreCase(athleteEmail).orElseThrow().getAthlete().getId();
    }

    private void plugStrava(UUID athleteId) {
        DeviceConnection conn = new DeviceConnection();
        conn.setAthlete(athleteRepository.findById(athleteId).orElseThrow());
        conn.setProvider(DeviceProvider.STRAVA);
        conn.setProviderAthleteId("424242");
        conn.setAccessToken("access");
        conn.setRefreshToken("refresh");
        conn.setExpiresAt(Instant.now().plusSeconds(3600).getEpochSecond());
        connectionRepository.saveAndFlush(conn);
    }

    private String sendRequest() throws Exception {
        return sendRequestTo(slug);
    }

    private String sendRequestTo(String coachSlug) throws Exception {
        return json(mvc.perform(post("/me/coaching-requests")
                .header("Authorization", athleteBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content("{\"coachSlug\":\"" + coachSlug + "\",\"message\":\""
                        + "Je prépare un premier marathon au printemps prochain.\"}"))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private void acceptCoachingRequest(String requestId) throws Exception {
        mvc.perform(post("/me/received-requests/{id}/accept", requestId)
                .header("Authorization", coachBearer)).andExpect(status().isOk());
    }

    private String publishSecondCoachProfile() throws Exception {
        String saved = coachBearer;
        coachBearer = bearer(DemoSeedService.COACH_EMAIL);
        try {
            return publishCoachProfile();
        } finally {
            coachBearer = saved;
        }
    }

    /**
     * Inscrit un athlète et vérifie son adresse : `CoachingRequestService` refuse une demande
     * venue d'une adresse que personne ne contrôle, et le coach doit pouvoir répondre.
     */
    private String registerAthlete(String email) throws Exception {
        JsonNode res = json(mvc.perform(post("/public/athlete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1234\","
                                + "\"firstName\":\"Lena\",\"lastName\":\"Martin\","
                                + "\"birthDate\":\"" + java.time.LocalDate.now().minusYears(28) + "\","
                                + "\"goal\":\"Finir mon premier marathon au printemps\","
                                + "\"termsAccepted\":true,\"healthDataConsent\":true}"))
                .andExpect(status().isCreated()));
        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
        return "Bearer " + res.get("accessToken").asText();
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearer(String email) throws Exception {
        return bearer(email, DemoSeedService.DEMO_PASSWORD);
    }

    private String bearer(String email, String password) throws Exception {
        return "Bearer " + MfaTestSupport.complete(mvc, context, email, json(mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()))).get("accessToken").asText();
    }

    private String publishCoachProfile() throws Exception {
        mvc.perform(put("/me/coach-profile").header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"headline\":\"Coach route\",\"bio\":\"" + "x".repeat(130) + "\","
                                + "\"disciplines\":[\"ROUTE\"],\"specialties\":[\"MARATHON\"],"
                                + "\"levels\":[],\"languages\":[\"fr\"],\"remote\":true,"
                                + "\"inPerson\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/me/coach-profile/offers").header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"name\":\"Suivi mensuel\",\"amountCents\":9000,"
                                + "\"periodicity\":\"MONTHLY\",\"active\":true,\"position\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/me/coach-profile/submit").header("Authorization", coachBearer))
                .andExpect(status().isOk());
        JsonNode queue = json(mvc.perform(get("/admin/coach-profiles").param("status", "PENDING")
                .header("Authorization", adminBearer)));
        mvc.perform(post("/admin/coach-profiles/{id}/approve",
                        queue.get("content").get(0).get("id").asText())
                .header("Authorization", adminBearer)).andExpect(status().isOk());
        return json(mvc.perform(get("/me/coach-profile").header("Authorization", coachBearer)))
                .get("slug").asText();
    }
}
