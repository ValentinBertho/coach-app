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
 * La sortie d'un coaching ne laisse rien derrière elle qui continue de tourner.
 *
 * <p>Quatre défauts fermés ici : la montre restait branchée sur la fiche quittée (et ses sorties
 * arrivaient chez l'ancien coach), un second coach pouvait accepter un athlète déjà suivi sans
 * que la première relation se ferme, un athlète invité par son coach se retrouvait sans compte
 * athlète en partant, et archiver la fiche d'un athlète qui se connecte ne lui disait rien.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachingExitTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.coachrun.repository.UserRepository userRepository;
    @Autowired private AthleteRepository athleteRepository;
    @Autowired private AthleteAccountRepository accountRepository;
    @Autowired private CoachAthleteRelationRepository relationRepository;
    @Autowired private DeviceConnectionRepository connectionRepository;

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
        athleteEmail = "exit-" + UUID.randomUUID() + "@exemple.fr";
        athleteBearer = registerAthlete(athleteEmail);
    }

    /** Après le départ, plus aucune sortie de la montre n'arrive chez l'ancien coach. */
    @Test
    void leavingUnplugsTheWatchFromTheFormerCoachFile() throws Exception {
        acceptCoachingRequest(sendRequest());
        UUID athleteId = currentAthleteId();
        plugStrava(athleteId);

        mvc.perform(post("/me/coach/end").header("Authorization", bearer(athleteEmail, "password1234"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());

        assertThat(connectionRepository.findByAthleteIdAndProvider(athleteId, DeviceProvider.STRAVA))
                .as("la connexion restait sur la fiche quittée et importait chez l'ancien coach")
                .isEmpty();
    }

    /** Un athlète déjà suivi ne peut être accepté par un second coach en silence. */
    @Test
    void aSecondCoachCannotTakeAnAthleteWhoIsStillCoached() throws Exception {
        String first = sendRequest();
        // Une seconde demande, partie AVANT l'acceptation de la première : c'est le cas réel.
        String second = sendRequestTo(publishSecondCoachProfile());
        acceptCoachingRequest(first);

        mvc.perform(post("/me/received-requests/{id}/accept", second)
                        .header("Authorization", bearer(DemoSeedService.COACH_EMAIL)))
                .andExpect(status().isConflict());
        assertThat(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(currentAthleteId()))
                .isPresent();
    }

    /** Une fois suivi, une nouvelle demande est refusée avec le chemin à suivre. */
    @Test
    void aCoachedAthleteIsToldToEndFirst() throws Exception {
        acceptCoachingRequest(sendRequest());
        athleteBearer = bearer(athleteEmail, "password1234");
        String body = mvc.perform(post("/me/coaching-requests").header("Authorization", athleteBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"coachSlug\":\"" + slug + "\",\"message\":\""
                                + "Je voudrais changer de coach pour la saison prochaine.\"}"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("Mettez d'abord fin");
    }

    /** Un athlète invité par son coach, sans compte athlète, en reçoit un en partant. */
    @Test
    void anInvitedAthleteGetsAnAccountWhenLeaving() throws Exception {
        String clubId = userRepository.findByEmailIgnoreCase(DemoSeedService.HEAD_COACH_EMAIL)
                .orElseThrow().getClub().getId().toString();
        String email = "invite-" + UUID.randomUUID() + "@exemple.fr";
        String athleteId = json(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Léa\",\"lastName\":\"Run\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated())).get("id").asText();
        String url = json(mvc.perform(post("/clubs/{c}/athletes/{a}/invitation", clubId, athleteId)
                .header("Authorization", coachBearer)).andExpect(status().isOk())).get("inviteUrl").asText();
        mvc.perform(post("/public/invitations/{t}/accept", url.substring(url.lastIndexOf('/') + 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"healthDataConsent\":true,\"termsAccepted\":true,"
                                + "\"email\":\"" + email + "\",\"password\":\"athletepass1\"}"))
                .andExpect(status().isOk());
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(accountRepository.findByUserId(user.getId()))
                .as("prémisse : un athlète invité n'a pas de compte athlète").isEmpty();

        mvc.perform(post("/me/coach/end").header("Authorization", bearer(email, "athletepass1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());

        assertThat(accountRepository.findByUserId(user.getId())).isPresent();
        mvc.perform(get("/me/account").header("Authorization", bearer(email, "athletepass1")))
                .andExpect(status().isOk());
    }

    /** Archiver la fiche d'un athlète qui se connecte met fin à son coaching. */
    @Test
    void archivingAConnectedAthleteEndsTheCoaching() throws Exception {
        acceptCoachingRequest(sendRequest());
        UUID athleteId = currentAthleteId();
        UUID clubId = athleteRepository.findById(athleteId).orElseThrow().getClub().getId();

        mvc.perform(delete("/clubs/{c}/athletes/{a}", clubId, athleteId).header("Authorization", coachBearer))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByEmailIgnoreCase(athleteEmail).orElseThrow().getAthlete()).isNull();
        assertThat(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(athleteId)).isEmpty();
    }

    /** La fiche détaillée dit au coach si la personne se connecte à l'application. */
    @Test
    void theDetailSaysWhetherThePersonUsesTheApp() throws Exception {
        acceptCoachingRequest(sendRequest());
        UUID athleteId = currentAthleteId();
        UUID clubId = athleteRepository.findById(athleteId).orElseThrow().getClub().getId();
        JsonNode detail = json(mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, athleteId)
                .header("Authorization", coachBearer)).andExpect(status().isOk()));
        assertThat(detail.get("appAccount").asBoolean()).isTrue();
    }

    // ---------------------------------------------------------------- outillage

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
