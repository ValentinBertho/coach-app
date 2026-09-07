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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un athlète venu du hub n'apparaît pas dans la liste des collègues de son coach.
 *
 * <h2>La promesse, et où elle était démentie</h2>
 *
 * <p>L'annuaire dit à l'athlète qu'il choisit <b>un coach</b>, pas un club. L'acceptation crée donc
 * sa fiche en privé — {@code coach_athlete_relations.club_id IS NULL} — et
 * {@code AthleteAccessValidator} tient cette promesse sur la fiche elle-même.</p>
 *
 * <p>La <b>liste</b> des athlètes, elle, ne filtrait que par club. Les autres coachs du club
 * voyaient donc le nom, le niveau et le groupe de quelqu'un qui n'avait jamais entendu parler
 * d'eux. Aucune donnée de santé ne fuitait, et aucun ne pouvait écrire — mais un nom est une
 * donnée personnelle, et la promesse était fausse.</p>
 *
 * <p>Le mécanisme préexistait au hub ; c'est <b>d'où viennent</b> ces athlètes qui a changé.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PrivateAthleteIsNotListedToColleaguesTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.coachrun.repository.UserRepository userRepository;

    private MockMvc mvc;
    private String referentBearer;
    private String colleagueBearer;
    private String adminBearer;
    private String clubId;
    private String hubAthleteName;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        referentBearer = bearer(DemoSeedService.HEAD_COACH_EMAIL);
        colleagueBearer = bearer(DemoSeedService.COACH_EMAIL);
        adminBearer = bearer(DemoSeedService.ADMIN_EMAIL);

        String slug = publishCoachProfile();
        hubAthleteName = "Hublette" + UUID.randomUUID().toString().substring(0, 8);
        String athlete = registerAthlete("hub-" + UUID.randomUUID() + "@exemple.fr", hubAthleteName);
        acceptCoachingRequest(athlete, slug);

        clubId = json(mvc.perform(get("/me").header("Authorization", athlete)))
                .get("clubId").asText();
    }

    /** Le référent le voit : c'est son athlète, c'est lui qui a accepté. */
    @Test
    void theReferentSeesTheirHubAthlete() throws Exception {
        assertThat(listedNames(referentBearer))
                .as("le coach qui a accepté la demande doit voir l'athlète dans sa liste")
                .contains(hubAthleteName);
    }

    /**
     * Le collègue ne le voit pas — c'est le correctif.
     *
     * <p>Il appartient au même club, il accède donc à la route ; ce qu'il ne doit pas obtenir,
     * c'est la ligne d'un athlète privé qui n'a pas choisi son club mais une personne.</p>
     */
    @Test
    void aColleagueOfTheSameClubDoesNotSeeThem() throws Exception {
        assertThat(listedNames(colleagueBearer))
                .as("l'athlète a choisi un coach, pas un club : ses collègues n'ont pas à le lister")
                .doesNotContain(hubAthleteName);
    }

    /**
     * L'administrateur plateforme, lui, le voit.
     *
     * <p>Sa vue transverse est déjà la règle sur la fiche : un support qui ne voit pas la moitié
     * d'un club ne peut pas faire son travail. La liste s'aligne dessus plutôt que d'inventer une
     * seconde règle.</p>
     */
    @Test
    void thePlatformAdminStillSeesEveryone() throws Exception {
        assertThat(listedNames(adminBearer))
                .as("l'accès transverse de l'administration ne change pas")
                .contains(hubAthleteName);
    }

    /**
     * Les athlètes du club restent visibles de tous ses coachs.
     *
     * <p>Le garde-fou du correctif : filtrer les privés ne doit pas vider la liste de tout le
     * monde. Le jeu de démonstration crée une dizaine d'athlètes rattachés au club.</p>
     */
    @Test
    void ordinaryClubAthletesStayVisibleToEveryCoach() throws Exception {
        assertThat(listedNames(colleagueBearer))
                .as("les athlètes du club, eux, se listent comme avant")
                .isNotEmpty();
    }

    // ---------------------------------------------------------------- outillage

    private String listedNames(String bearer) throws Exception {
        return body(mvc.perform(get("/clubs/{c}/athletes", clubId)
                .param("size", "100")
                .header("Authorization", bearer)).andExpect(status().isOk()));
    }

    private void acceptCoachingRequest(String athleteBearer, String slug) throws Exception {
        String requestId = json(mvc.perform(post("/me/coaching-requests")
                .header("Authorization", athleteBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content("{\"coachSlug\":\"" + slug + "\",\"message\":\""
                        + "Je prépare un premier marathon au printemps prochain.\"}"))
                .andExpect(status().isCreated())).get("id").asText();
        mvc.perform(post("/me/received-requests/{id}/accept", requestId)
                .header("Authorization", referentBearer)).andExpect(status().isOk());
    }

    private String registerAthlete(String email, String lastName) throws Exception {
        JsonNode res = json(mvc.perform(post("/public/athlete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\","
                                + "\"firstName\":\"Lena\",\"lastName\":\"" + lastName + "\","
                                + "\"birthDate\":\"" + LocalDate.now().minusYears(28) + "\","
                                + "\"goal\":\"Finir mon premier marathon au printemps\","
                                + "\"termsAccepted\":true,\"healthDataConsent\":true}"))
                .andExpect(status().isCreated()));
        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
        return "Bearer " + res.get("accessToken").asText();
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(body(actions));
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String bearer(String email) throws Exception {
        return "Bearer " + json(mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content("{\"email\":\"" + email + "\",\"password\":\""
                        + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    private String publishCoachProfile() throws Exception {
        mvc.perform(put("/me/coach-profile").header("Authorization", referentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"headline\":\"Coach route\",\"bio\":\"" + "x".repeat(130) + "\","
                                + "\"disciplines\":[\"ROUTE\"],\"specialties\":[\"MARATHON\"],"
                                + "\"levels\":[],\"languages\":[\"fr\"],\"remote\":true,"
                                + "\"inPerson\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/me/coach-profile/offers").header("Authorization", referentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"name\":\"Suivi mensuel\",\"amountCents\":9000,"
                                + "\"periodicity\":\"MONTHLY\",\"active\":true,\"position\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/me/coach-profile/submit").header("Authorization", referentBearer))
                .andExpect(status().isOk());
        JsonNode queue = json(mvc.perform(get("/admin/coach-profiles").param("status", "PENDING")
                .header("Authorization", adminBearer)));
        mvc.perform(post("/admin/coach-profiles/{id}/approve",
                        queue.get("content").get(0).get("id").asText())
                .header("Authorization", adminBearer)).andExpect(status().isOk());
        return json(mvc.perform(get("/me/coach-profile").header("Authorization", referentBearer)))
                .get("slug").asText();
    }
}
