package com.coachrun;

import com.coachrun.dto.request.LactateTestRequest;
import com.coachrun.dto.request.LactateTestStepRequest;
import com.coachrun.entity.AthleteProposal;
import com.coachrun.entity.enums.FieldsPreset;
import com.coachrun.entity.enums.ProposalType;
import com.coachrun.entity.enums.TestType;
import com.coachrun.repository.AthleteProposalRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.service.DemoSeedService;
import com.coachrun.service.LactateTestService;
import com.coachrun.service.StrengthScheduleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Faille d'autorisation de l'audit du 10/11/2026 (B1) : une ressource d'athlète (séance, sortie,
 * objectif, note, test…) était cherchée dans le club sans vérifier qu'elle appartenait à
 * l'athlète de l'adresse appelée. Le contrôle d'accès ne portait que sur cet athlète : un coach
 * autorisé sur A lisait, renommait ou supprimait les données de B en glissant l'identifiant de B
 * sous l'adresse de A — y compris quand B est l'athlète privé d'un autre coach.
 *
 * <p>Ce test rejoue chacune de ces routes dans cette position : l'assistant a le droit
 * d'écriture sur l'athlète démo (A), et vise les ressources d'un athlète privé (B) qu'il ne doit
 * jamais voir. Toutes doivent répondre 404 — la même réponse qu'une ressource absente, pour ne
 * rien révéler — et rien ne doit avoir bougé chez B.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CrossAthleteAuthorizationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AthleteRepository athleteRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private AthleteProposalRepository proposalRepository;
    @Autowired private LactateTestService lactateTestService;
    @Autowired private StrengthScheduleService strengthScheduleService;

    private MockMvc mvc;
    private String ownerBearer;
    private String assistantBearer;
    private String clubId;
    private String athleteA;   // accessible à l'assistant (écriture accordée ci-dessous)
    private String athleteB;   // privé du coach référent : jamais accessible à l'assistant

    private String workoutB;
    private String activityB;
    private String raceB;
    private String noteB;
    private String unavailabilityB;
    private String lactateTestB;
    private String scheduledB;
    private String proposalB;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        JsonNode owner = login(DemoSeedService.HEAD_COACH_EMAIL);
        ownerBearer = "Bearer " + owner.get("accessToken").asText();
        clubId = owner.get("user").get("clubId").asText();
        JsonNode assistant = login(DemoSeedService.COACH_EMAIL);
        assistantBearer = "Bearer " + assistant.get("accessToken").asText();
        String assistantUserId = assistant.get("user").get("id").asText();
        athleteA = login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText();

        // L'assistant passe en écriture sur A : c'est la position la plus forte d'un coach
        // légitime, et celle d'où partait l'attaque.
        mvc.perform(put("/clubs/{c}/athletes/{a}/permissions/{u}", clubId, athleteA, assistantUserId)
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"WRITE\"}"))
                .andExpect(status().isOk());

        athleteB = findPrivateAthlete();
        UUID club = UUID.fromString(clubId);
        UUID b = UUID.fromString(athleteB);

        workoutB = jdbc.queryForObject(
                "select cast(id as varchar) from workouts where athlete_id = ? order by scheduled_date limit 1",
                String.class, b);
        activityB = jdbc.queryForObject(
                "select cast(id as varchar) from activities where athlete_id = ? limit 1", String.class, b);

        raceB = createAsOwner("/races",
                "{\"name\":\"Course de B\",\"raceDate\":\"" + LocalDate.now().plusDays(60) + "\",\"distanceM\":10000}");
        noteB = createAsOwner("/notes",
                "{\"noteDate\":\"" + LocalDate.now() + "\",\"text\":\"Note de B\"}");
        unavailabilityB = createAsOwner("/unavailabilities",
                "{\"startDate\":\"" + LocalDate.now().plusDays(3) + "\",\"endDate\":\""
                        + LocalDate.now().plusDays(5) + "\",\"reason\":\"OTHER\"}");

        var athlete = athleteRepository.findById(b).orElseThrow();
        athlete.setHealthDataConsentAt(Instant.now());
        lactateTestB = lactateTestService.create(club, b, new LactateTestRequest(
                TestType.LACTATE, LocalDate.now().minusDays(10), "Test de B", BigDecimal.ONE, 60, 190,
                false, List.of(step(3.0, 1.0), step(3.5, 1.6), step(4.0, 3.2), step(4.5, 6.0))))
                .id().toString();

        UUID strengthSession = jdbc.queryForObject(
                "select id from strength_sessions where club_id = ? limit 1", UUID.class, club);
        scheduledB = strengthScheduleService.schedule(club, b, strengthSession,
                LocalDate.now().plusDays(2), FieldsPreset.DEBUTANT).id().toString();

        AthleteProposal p = new AthleteProposal();
        p.setClub(clubRepository.findById(club).orElseThrow());
        p.setAthlete(athlete);
        p.setType(ProposalType.SESSION_DROP);
        p.setTargetId(UUID.fromString(workoutB));
        p.setTitle("Supprimer la séance de B");
        p.setDedupeKey("test:" + UUID.randomUUID());
        proposalB = proposalRepository.save(p).getId().toString();
    }

    /** Chaque route ressource-d'athlète, appelée sous A avec l'identifiant d'une ressource de B. */
    @Test
    void everyChildRouteRejectsAResourceOfAnotherAthlete() throws Exception {
        String base = "/clubs/" + clubId + "/athletes/" + athleteA;
        String date = "{\"scheduledDate\":\"" + LocalDate.now().plusDays(1) + "\"}";
        String emptyStructure = "{\"warmup\":[],\"main\":[],\"cooldown\":[]}";

        List<Call> calls = new ArrayList<>(List.of(
                // Séances
                new Call(HttpMethod.GET, base + "/workouts/" + workoutB, null),
                new Call(HttpMethod.GET, base + "/workouts/" + workoutB + "/prescription", null),
                new Call(HttpMethod.GET, base + "/workouts/" + workoutB + "/thread", null),
                new Call(HttpMethod.GET, base + "/workouts/" + workoutB + "/compliance", null),
                new Call(HttpMethod.PUT, base + "/workouts/" + workoutB + "/structure", emptyStructure),
                new Call(HttpMethod.PATCH, base + "/workouts/" + workoutB + "/reschedule", date),
                new Call(HttpMethod.PATCH, base + "/workouts/" + workoutB + "/coach-comment", "{\"comment\":\"x\"}"),
                new Call(HttpMethod.PATCH, base + "/workouts/" + workoutB + "/title", "{\"title\":\"Piraté\"}"),
                new Call(HttpMethod.PATCH, base + "/workouts/" + workoutB + "/instructions", "{\"notes\":\"Piraté\"}"),
                new Call(HttpMethod.POST, base + "/workouts/" + workoutB + "/reply/read", null),
                new Call(HttpMethod.PATCH, base + "/workouts/" + workoutB + "/reviewed?reviewed=true", null),
                new Call(HttpMethod.POST, base + "/workouts/" + workoutB + "/acknowledge", null),
                new Call(HttpMethod.POST, base + "/workouts/" + workoutB + "/copy", date),
                new Call(HttpMethod.PATCH, base + "/workouts/" + workoutB + "/status", "{\"status\":\"MISSED\"}"),
                new Call(HttpMethod.POST, base + "/workouts/" + workoutB + "/save-as-template",
                        "{\"name\":\"Volé\",\"title\":\"Volé\"}"),
                new Call(HttpMethod.POST, base + "/workouts/copy-from",
                        "{\"sourceWorkoutId\":\"" + workoutB + "\",\"scheduledDate\":\"" + LocalDate.now().plusDays(1) + "\"}"),
                new Call(HttpMethod.DELETE, base + "/workouts/" + workoutB, null),
                // Sorties
                new Call(HttpMethod.GET, base + "/activities/" + activityB + "/time-in-zone", null),
                new Call(HttpMethod.GET, base + "/activities/" + activityB + "/route", null),
                new Call(HttpMethod.GET, base + "/activities/" + activityB + "/laps", null),
                new Call(HttpMethod.GET, base + "/activities/" + activityB + "/stream", null),
                new Call(HttpMethod.DELETE, base + "/activities/" + activityB + "/match", null),
                // Objectifs de course (et ce qui en découle : plan, résultat)
                new Call(HttpMethod.PUT, base + "/races/" + raceB,
                        "{\"name\":\"Piraté\",\"raceDate\":\"" + LocalDate.now().plusDays(60) + "\"}"),
                new Call(HttpMethod.POST, base + "/races/" + raceB + "/result", "{\"timeSeconds\":2400}"),
                new Call(HttpMethod.POST, base + "/races/" + raceB + "/plan/preview", "{}"),
                new Call(HttpMethod.POST, base + "/races/" + raceB + "/plan/apply", "{}"),
                new Call(HttpMethod.DELETE, base + "/races/" + raceB, null),
                // Notes, indisponibilités, tests lactate
                new Call(HttpMethod.PUT, base + "/notes/" + noteB,
                        "{\"noteDate\":\"" + LocalDate.now() + "\",\"text\":\"Piraté\"}"),
                new Call(HttpMethod.DELETE, base + "/notes/" + noteB, null),
                new Call(HttpMethod.PUT, base + "/unavailabilities/" + unavailabilityB,
                        "{\"startDate\":\"" + LocalDate.now() + "\",\"endDate\":\"" + LocalDate.now()
                                + "\",\"reason\":\"OTHER\"}"),
                new Call(HttpMethod.DELETE, base + "/unavailabilities/" + unavailabilityB, null),
                new Call(HttpMethod.GET, base + "/lactate-tests/" + lactateTestB, null),
                new Call(HttpMethod.DELETE, base + "/lactate-tests/" + lactateTestB, null),
                // Préparation physique
                new Call(HttpMethod.GET, base + "/pp/scheduled/" + scheduledB + "/prescription", null),
                new Call(HttpMethod.POST, base + "/pp/scheduled/copy-from",
                        "{\"sourceScheduledId\":\"" + scheduledB + "\",\"scheduledDate\":\"" + LocalDate.now().plusDays(1) + "\"}"),
                new Call(HttpMethod.DELETE, base + "/pp/scheduled/" + scheduledB, null),
                // Propositions (hors adresse d'athlète : vérifiées sur l'athlète de la proposition)
                new Call(HttpMethod.POST, "/clubs/" + clubId + "/proposals/" + proposalB + "/accept", null),
                new Call(HttpMethod.POST, "/clubs/" + clubId + "/proposals/" + proposalB + "/dismiss", null)));

        List<String> leaks = new ArrayList<>();
        for (Call call : calls) {
            var req = request(call.method(), call.url()).header("Authorization", assistantBearer);
            if (call.body() != null) {
                req = req.contentType(MediaType.APPLICATION_JSON).content(call.body());
            }
            int code = mvc.perform(req).andReturn().getResponse().getStatus();
            if (code != 404) {
                leaks.add(call.method() + " " + call.url() + " → " + code);
            }
        }
        assertThat(leaks).as("routes qui n'ont pas répondu 404 pour une ressource d'un autre athlète").isEmpty();

        // Rien n'a bougé chez B.
        UUID w = UUID.fromString(workoutB);
        assertThat(jdbc.queryForObject("select count(*) from workouts where id = ?", Integer.class, w)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select title from workouts where id = ?", String.class, w)).isNotEqualTo("Piraté");
        assertThat(jdbc.queryForObject("select count(*) from race_objectives where id = ?", Integer.class,
                UUID.fromString(raceB))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from calendar_notes where id = ?", Integer.class,
                UUID.fromString(noteB))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from athlete_proposals where id = ?", String.class,
                UUID.fromString(proposalB))).isEqualTo("PENDING");
    }

    /** Témoin : sous la bonne adresse, le référent atteint bien les mêmes ressources. */
    @Test
    void theOwnerStillReachesTheResourcesUnderTheRightAthlete() throws Exception {
        String base = "/clubs/" + clubId + "/athletes/" + athleteB;
        for (String url : List.of(
                base + "/workouts/" + workoutB,
                base + "/workouts/" + workoutB + "/prescription",
                base + "/activities/" + activityB + "/route",
                base + "/lactate-tests/" + lactateTestB,
                base + "/pp/scheduled/" + scheduledB + "/prescription")) {
            mvc.perform(get(url).header("Authorization", ownerBearer)).andExpect(status().isOk());
        }
        mvc.perform(post(base + "/workouts/copy-from").header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceWorkoutId\":\"" + workoutB + "\",\"scheduledDate\":\""
                                + LocalDate.now().plusDays(1) + "\"}"))
                .andExpect(status().isCreated());
    }

    private record Call(HttpMethod method, String url, String body) {
    }

    private String findPrivateAthlete() throws Exception {
        JsonNode page = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes", clubId).param("size", "50")
                                .header("Authorization", ownerBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode a : page.get("content")) {
            String id = a.get("id").asText();
            int code = mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, id)
                            .header("Authorization", assistantBearer))
                    .andReturn().getResponse().getStatus();
            if (code == 403) {
                Integer workouts = jdbc.queryForObject(
                        "select count(*) from workouts where athlete_id = ?", Integer.class, UUID.fromString(id));
                Integer activities = jdbc.queryForObject(
                        "select count(*) from activities where athlete_id = ?", Integer.class, UUID.fromString(id));
                if (workouts != null && workouts > 0 && activities != null && activities > 0) {
                    return id;
                }
            }
        }
        throw new IllegalStateException("Le jeu démo devrait contenir un athlète privé avec séances et sorties.");
    }

    private String createAsOwner(String path, String body) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}" + path, clubId, athleteB)
                                .header("Authorization", ownerBearer)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    private static LactateTestStepRequest step(double speedMs, double lactate) {
        return new LactateTestStepRequest(BigDecimal.valueOf(speedMs), 150, BigDecimal.valueOf(lactate), null, 180);
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
