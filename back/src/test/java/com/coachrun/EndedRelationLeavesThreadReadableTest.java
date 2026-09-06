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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Après une fin de coaching, le fil se lit encore — et ne s'écrit plus.
 *
 * <h2>Le défaut que ce fichier ferme</h2>
 *
 * <p>Mettre fin au coaching détache le compte de sa fiche ({@code user.athlete = null}). Or
 * {@code ConversationService} déduisait la participation de ce lien : l'athlète perdait donc
 * l'accès à <b>ses propres messages</b>, et sa boîte revenait vide. Le coach, lui, les gardait —
 * le repli en lecture de l'ancien référent (lot 0) le lui laisse.</p>
 *
 * <p>L'asymétrie était difficile à défendre : c'est l'athlète qui a écrit la moitié de ce fil, et
 * c'est lui qui, en partant, le perdait. L'audit annonçait ce point comme « non fait » ; il l'est
 * désormais, dans le sens qui rend la sortie supportable — on relit ce qui s'est dit, personne ne
 * peut plus l'alimenter.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EndedRelationLeavesThreadReadableTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.coachrun.repository.UserRepository userRepository;

    private MockMvc mvc;
    private String coachBearer;
    private String adminBearer;
    private String athleteBearer;
    private String conversationId;
    private String clubId;
    private String athleteId;
    private String athleteEmail;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        coachBearer = bearer(DemoSeedService.HEAD_COACH_EMAIL);
        adminBearer = bearer(DemoSeedService.ADMIN_EMAIL);

        String slug = publishCoachProfile();
        athleteEmail = "lena-" + java.util.UUID.randomUUID() + "@exemple.fr";
        athleteBearer = registerAthlete(athleteEmail);
        acceptCoachingRequest(slug);
        // Le jeton d'inscription a été émis AVANT que la fiche existe : il porte encore
        // `athleteId = null`. On se reconnecte, comme le fait l'application.
        athleteBearer = bearer(athleteEmail, "password123");

        JsonNode me = json(mvc.perform(get("/me").header("Authorization", athleteBearer)));
        athleteId = me.get("athleteId").asText();
        clubId = me.get("clubId").asText();

        // Un échange, pour qu'il y ait quelque chose à relire. Ouvert par le coach : un athlète
        // venu du hub est PRIVÉ à son référent, et sa liste de destinataires ne passe pas par le
        // club — c'est le coach qui a l'athlète dans la sienne.
        conversationId = json(mvc.perform(post("/me/conversations/open")
                .header("Authorization", coachBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content("{\"kind\":\"ATHLETE\",\"targetId\":\"" + athleteId + "\"}"))
                .andExpect(status().isOk())).get("id").asText();
        postMessage(athleteBearer, "Bonjour, hâte de commencer le plan marathon.")
                .andExpect(status().isCreated());
        postMessage(coachBearer, "Bienvenue, on démarre lundi.")
                .andExpect(status().isCreated());
    }

    /**
     * Le cas central : l'athlète part, et retrouve quand même son fil.
     *
     * <p>Sa boîte de réception le montre encore — sans quoi la règle de lecture ne servirait à
     * rien, faute d'un chemin pour y arriver — et les messages se relisent.</p>
     */
    @Test
    void theAthleteStillReadsTheThreadAfterLeaving() throws Exception {
        endByAthlete();

        JsonNode inbox = json(mvc.perform(get("/me/conversations")
                .header("Authorization", athleteBearer)).andExpect(status().isOk()));
        assertThat(inbox)
                .as("un compte détaché voyait sa boîte vide : c'est le défaut corrigé")
                .isNotEmpty();

        JsonNode messages = json(mvc.perform(get("/me/conversations/{id}/messages", conversationId)
                .header("Authorization", athleteBearer)).andExpect(status().isOk()));
        assertThat(messages).hasSize(2);
        assertThat(messages.toString()).contains("hâte de commencer");
    }

    /**
     * Lecture seule, et c'est le point qui rend la sortie réelle.
     *
     * <p>Un fil qui resterait ouvert donnerait à un ancien coach un canal vers quelqu'un qu'il ne
     * suit plus, et à l'athlète le sentiment que partir ne ferme rien.</p>
     */
    @Test
    void neitherSideCanStillWriteToIt() throws Exception {
        endByAthlete();

        // 409 et non 404 : le fil existe toujours et se lit — l'écran doit pouvoir dire pourquoi
        // le champ de saisie est fermé, pas prétendre que la conversation a disparu.
        String athleteRefusal = postMessage(athleteBearer, "Une dernière chose…")
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(athleteRefusal).contains("lecture seule");

        postMessage(coachBearer, "Reviens quand tu veux.")
                .andExpect(status().isConflict());
    }

    /**
     * Le coach relit, lui aussi — il a écrit l'autre moitié.
     *
     * <p>Il l'a toujours pu, par le repli en lecture de l'ancien référent ; ce test fixe le fait
     * que la fermeture de l'écriture ne le lui a pas retiré au passage.</p>
     */
    @Test
    void theCoachStillReadsTheThreadToo() throws Exception {
        endByAthlete();

        JsonNode messages = json(mvc.perform(get("/me/conversations/{id}/messages", conversationId)
                .header("Authorization", coachBearer)).andExpect(status().isOk()));
        assertThat(messages).hasSize(2);
    }

    /** Tant que la relation dure, rien ne change : les deux écrivent. */
    @Test
    void bothSidesStillWriteWhileTheRelationLasts() throws Exception {
        postMessage(athleteBearer, "Question sur la séance de jeudi.")
                .andExpect(status().isCreated());
        postMessage(coachBearer, "On la décale à vendredi.")
                .andExpect(status().isCreated());
    }

    /**
     * Un fil clos ne s'ouvre pas à un tiers.
     *
     * <p>La lecture rendue est celle de <b>l'ancien participant</b>, pas celle du club : sans
     * cette borne, « l'ancien coach garde la lecture » deviendrait « n'importe quel coach lit les
     * conversations privées d'un athlète que plus personne ne suit ».</p>
     */
    @Test
    void anotherCoachNeverReadsTheClosedThread() throws Exception {
        endByAthlete();

        mvc.perform(get("/me/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(DemoSeedService.COACH_EMAIL)))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- outillage

    /**
     * L'athlète met fin au coaching, puis se reconnecte.
     *
     * <p>La reconnexion n'est pas un artifice de test : le détachement <b>ferme les sessions</b>
     * du compte, parce que le jeton affirme un {@code athleteId} et un {@code clubId} qu'il ne
     * relit jamais en base. C'est ce que fait l'application, et c'est ce qui garantit qu'un ancien
     * athlète ne se présente plus comme membre du club de son ancien coach.</p>
     */
    private void endByAthlete() throws Exception {
        mvc.perform(post("/me/coach/end").header("Authorization", athleteBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"note\":\"Je change d'objectif.\"}"))
                .andExpect(status().isNoContent());
        athleteBearer = bearer(athleteEmail, "password123");
    }

    private ResultActions postMessage(String bearer, String body) throws Exception {
        return mvc.perform(post("/me/conversations/{id}/messages", conversationId)
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(objectMapper.writeValueAsString(java.util.Map.of("body", body))));
    }

    /**
     * Inscrit un athlète et vérifie son adresse : `CoachingRequestService` refuse une demande
     * venue d'une adresse que personne ne contrôle, et le coach doit pouvoir répondre.
     */
    private String registerAthlete(String email) throws Exception {
        JsonNode res = json(mvc.perform(post("/public/athlete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\","
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

    private void acceptCoachingRequest(String slug) throws Exception {
        String requestId = json(mvc.perform(post("/me/coaching-requests")
                .header("Authorization", athleteBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content("{\"coachSlug\":\"" + slug + "\",\"message\":\""
                        + "Je prépare un premier marathon au printemps prochain.\"}"))
                .andExpect(status().isCreated())).get("id").asText();
        mvc.perform(post("/me/received-requests/{id}/accept", requestId)
                .header("Authorization", coachBearer)).andExpect(status().isOk());
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearer(String email) throws Exception {
        return bearer(email, DemoSeedService.DEMO_PASSWORD);
    }

    private String bearer(String email, String password) throws Exception {
        return "Bearer " + json(mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())).get("accessToken").asText();
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
