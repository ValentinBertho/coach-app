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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le parcours complet du hub : un athlète s'inscrit seul, trouve un coach, demande, et travaille.
 *
 * <p>C'est le test qui dit si la boucle est fermée. Tout le reste — vitrine, annuaire, photo — n'a
 * de sens que si ce chemin-là aboutit : un compte sans coach, une demande, une acceptation, et un
 * athlète qui a soudain un calendrier.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachingRequestFlowTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.coachrun.repository.UserRepository userRepository;

    private MockMvc mvc;
    private String coachBearer;
    private String adminBearer;
    private String slug;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        coachBearer = bearer(DemoSeedService.HEAD_COACH_EMAIL);
        adminBearer = bearer(DemoSeedService.ADMIN_EMAIL);
        slug = publishCoachProfile();
    }

    /**
     * Le parcours nominal, de bout en bout.
     *
     * <p>Avant l'acceptation, l'athlète n'a <b>pas de fiche</b> : c'est l'inversion que tout le hub
     * organise, et le compte doit exister sans elle. Après, il en a une, dans l'espace du coach.</p>
     */
    @Test
    void anAthleteSignsUpAloneThenGetsACoach() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");

        // Un athlète tout neuf n'appartient à personne : ni club, ni fiche.
        JsonNode me = json(mvc.perform(get("/me").header("Authorization", athlete)));
        assertThat(me.get("athleteId").isNull()).as("aucune fiche avant d'avoir un coach").isTrue();
        assertThat(me.get("clubId").isNull()).as("aucun club non plus").isTrue();

        String requestId = submitRequest(athlete);

        JsonNode inbox = json(mvc.perform(get("/me/received-requests")
                .header("Authorization", coachBearer)).andExpect(status().isOk()));
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).get("athleteGoal").asText()).contains("premier marathon");

        mvc.perform(post("/me/received-requests/{id}/accept", requestId)
                .header("Authorization", coachBearer)).andExpect(status().isOk());

        // Après acceptation, l'athlète a une fiche et un espace.
        JsonNode after = json(mvc.perform(get("/me").header("Authorization", athlete)));
        assertThat(after.get("athleteId").isNull()).as("la fiche existe désormais").isFalse();
        assertThat(after.get("clubId").isNull()).as("il a rejoint l'espace du coach").isFalse();
    }

    /**
     * L'athlète venu du hub est <b>privé</b> : il a choisi un coach, pas une organisation. Un autre
     * coach du même espace ne doit pas le voir.
     */
    @Test
    void anAthleteFromTheHubIsPrivateToTheCoachWhoAccepted() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        String requestId = submitRequest(athlete);
        mvc.perform(post("/me/received-requests/{id}/accept", requestId)
                .header("Authorization", coachBearer)).andExpect(status().isOk());

        String athleteId = json(mvc.perform(get("/me").header("Authorization", athlete)))
                .get("athleteId").asText();
        String clubId = json(mvc.perform(get("/me").header("Authorization", athlete)))
                .get("clubId").asText();

        String assistant = bearer(DemoSeedService.COACH_EMAIL);
        mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, athleteId)
                        .header("Authorization", assistant))
                .andExpect(status().isForbidden());

        mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, athleteId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isOk());
    }

    /** Une adresse non vérifiée ne peut pas solliciter : le coach doit pouvoir répondre. */
    @Test
    void anUnverifiedAthleteCannotSolicitACoach() throws Exception {
        String athlete = registerAthleteWithoutVerifying("pas.verifie@exemple.fr");

        mvc.perform(post("/me/coaching-requests").header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(requestBody()))
                .andExpect(status().isForbidden());
    }

    /** Une seule demande en attente par couple : une file noyée est une file qu'on n'ouvre plus. */
    @Test
    void anAthleteCannotFloodTheSameCoach() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        submitRequest(athlete);

        mvc.perform(post("/me/coaching-requests").header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(requestBody()))
                .andExpect(status().isConflict());
    }

    /** Le coach pose UNE question, l'athlète répond UNE fois : il n'y a pas de messagerie avant. */
    @Test
    void theExchangeBeforeAcceptanceIsOneQuestionAndOneAnswer() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        String requestId = submitRequest(athlete);

        mvc.perform(post("/me/received-requests/{id}/ask", requestId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"note\":\"Combien de séances par semaine ?\"}"))
                .andExpect(status().isOk());

        // Une seconde question est refusée : la suite se passe dans la messagerie, après l'accord.
        mvc.perform(post("/me/received-requests/{id}/ask", requestId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"note\":\"Et vos disponibilités ?\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/me/coaching-requests/{id}/answer", requestId)
                        .header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"note\":\"Quatre.\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/me/coaching-requests/{id}/answer", requestId)
                        .header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"note\":\"En fait cinq.\"}"))
                .andExpect(status().isConflict());
    }

    /** Un refus porte son motif : sans lui, l'athlète redemande sans savoir ce qui n'allait pas. */
    @Test
    void aDeclineCarriesItsReasonToTheAthlete() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        String requestId = submitRequest(athlete);

        mvc.perform(post("/me/received-requests/{id}/decline", requestId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"note\":\"Je suis complet jusqu'en septembre.\"}"))
                .andExpect(status().isOk());

        JsonNode mine = json(mvc.perform(get("/me/coaching-requests").header("Authorization", athlete)));
        assertThat(mine.get(0).get("status").asText()).isEqualTo("DECLINED");
        assertThat(mine.get(0).get("declineReason").asText()).contains("complet");
    }

    /** Retirée n'est pas refusée : l'athlète doit pouvoir se raviser sans se croire éconduit. */
    @Test
    void withdrawingIsNotBeingDeclined() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        String requestId = submitRequest(athlete);

        mvc.perform(delete("/me/coaching-requests/{id}", requestId).header("Authorization", athlete))
                .andExpect(status().isOk());

        JsonNode mine = json(mvc.perform(get("/me/coaching-requests").header("Authorization", athlete)));
        assertThat(mine.get(0).get("status").asText()).isEqualTo("WITHDRAWN");
    }

    /**
     * Le coach ne voit pas les coordonnées de l'athlète tant qu'il n'a pas accepté. Si une demande
     * livrait l'adresse, il suffirait d'en recevoir pour se faire un fichier, et refuser
     * n'aurait plus d'effet.
     */
    @Test
    void theCoachSeesNoContactDetailsBeforeAccepting() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        submitRequest(athlete);

        String inbox = body(mvc.perform(get("/me/received-requests")
                .header("Authorization", coachBearer)).andExpect(status().isOk()));

        assertThat(inbox).doesNotContain("nina@exemple.fr");
        assertThat(inbox).doesNotContain("birthDate");
    }

    /** L'inscription libre est fermée aux moins de 16 ans, et le refus dit le chemin qui reste. */
    @Test
    void selfRegistrationIsClosedUnderSixteen() throws Exception {
        String body = mvc.perform(post("/public/athlete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(registrationBody("jeune@exemple.fr", LocalDate.now().minusYears(14))))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("16 ans");
        assertThat(body).as("le refus nomme le chemin qui reste ouvert").contains("inviter");
    }

    // ------------------------------------------------------------------ utilitaires

    private String submitRequest(String athleteBearer) throws Exception {
        return json(mvc.perform(post("/me/coaching-requests").header("Authorization", athleteBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(requestBody()))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private String requestBody() {
        return "{\"coachSlug\":\"" + slug + "\",\"message\":\"Je prépare mon premier marathon "
                + "au printemps et je cherche un accompagnement structuré.\"}";
    }

    /** Inscrit un athlète et vérifie son adresse — l'état normal avant de solliciter. */
    private String registerAthlete(String email) throws Exception {
        String bearer = registerAthleteWithoutVerifying(email);
        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
        return bearer;
    }

    private String registerAthleteWithoutVerifying(String email) throws Exception {
        JsonNode res = json(mvc.perform(post("/public/athlete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(registrationBody(email, LocalDate.now().minusYears(28))))
                .andExpect(status().isCreated()));
        return "Bearer " + res.get("accessToken").asText();
    }

    private String registrationBody(String email, LocalDate birthDate) {
        return "{\"email\":\"" + email + "\",\"password\":\"password123\","
                + "\"firstName\":\"Nina\",\"lastName\":\"Roy\","
                + "\"birthDate\":\"" + birthDate + "\","
                + "\"goal\":\"Finir mon premier marathon au printemps\","
                + "\"termsAccepted\":true,\"healthDataConsent\":true}";
    }

    /** Publie la fiche du coach de démonstration : sans elle, personne n'est sollicitable. */
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
        mvc.perform(post("/admin/coach-profiles/{id}/approve", queue.get("content").get(0).get("id").asText())
                .header("Authorization", adminBearer)).andExpect(status().isOk());
        return json(mvc.perform(get("/me/coach-profile").header("Authorization", coachBearer)))
                .get("slug").asText();
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return objectMapper.readTree(body(actions));
    }

    private String body(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String bearer(String email) throws Exception {
        JsonNode res = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        return "Bearer " + res.get("accessToken").asText();
    }
}
