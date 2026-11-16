package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.TrainingGroup;
import com.coachrun.entity.User;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.TrainingGroupRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les liens d'invitation : un coach envoie un lien, l'athlète rejoint en un geste — directement
 * (lien personnel) ou par une demande (lien public), dans le club et le groupe prévus.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InviteLinkTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TrainingGroupRepository groupRepository;
    @Autowired private CoachAthleteRelationRepository relationRepository;

    private MockMvc mvc;
    private String coachBearer;
    private User coach;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        coachBearer = bearer(DemoSeedService.HEAD_COACH_EMAIL);
        coach = userRepository.findByEmailIgnoreCase(DemoSeedService.HEAD_COACH_EMAIL).orElseThrow();
    }

    /** Lien personnel « club + groupe » : l'athlète est suivi dès qu'il l'ouvre, au bon endroit. */
    @Test
    void aPersonalLinkLetsTheAthleteInRightAway() throws Exception {
        TrainingGroup group = newGroup("Groupe du mardi");
        JsonNode link = createLink("{\"label\":\"Pour Léa\",\"clubMembership\":true,\"directEntry\":true,"
                + "\"groupId\":\"" + group.getId() + "\",\"maxUses\":1}");
        String token = link.get("token").asText();
        assertThat(link.get("url").asText()).endsWith("/j/" + token);

        JsonNode preview = json(mvc.perform(get("/public/invites/{t}", token)).andExpect(status().isOk()));
        assertThat(preview.get("valid").asBoolean()).isTrue();
        assertThat(preview.get("groupName").asText()).isEqualTo("Groupe du mardi");

        String email = "lea-" + UUID.randomUUID() + "@exemple.fr";
        String athlete = registerAthlete(email, true);
        JsonNode joined = json(mvc.perform(post("/me/invites/{t}/join", token).header("Authorization", athlete))
                .andExpect(status().isOk()));
        assertThat(joined.get("outcome").asText()).isEqualTo("JOINED");

        Athlete fiche = userRepository.findByEmailIgnoreCase(email).orElseThrow().getAthlete();
        assertThat(fiche.getClub().getId()).isEqualTo(coach.getClub().getId());
        assertThat(fiche.getGroup().getId()).isEqualTo(group.getId());
        CoachAthleteRelation relation = relationRepository
                .findByAthleteIdAndReferentTrueAndActiveTrue(fiche.getId()).orElseThrow();
        assertThat(relation.getClub()).as("athlète du club, pas seulement du coach").isNotNull();

        // Un seul usage prévu : le lien est épuisé.
        JsonNode after = json(mvc.perform(get("/public/invites/{t}", token)).andExpect(status().isOk()));
        assertThat(after.get("valid").asBoolean()).isFalse();
    }

    /** Lien public : une demande arrive chez le coach, qui l'accepte ; l'athlète reste privé. */
    @Test
    void aPublicLinkSendsARequestToValidate() throws Exception {
        String token = createLink("{\"label\":\"Instagram\"}").get("token").asText();
        String email = "tom-" + UUID.randomUUID() + "@exemple.fr";
        String athlete = registerAthlete(email, true);

        JsonNode res = json(mvc.perform(post("/me/invites/{t}/join", token).header("Authorization", athlete))
                .andExpect(status().isOk()));
        assertThat(res.get("outcome").asText()).isEqualTo("REQUESTED");

        JsonNode inbox = json(mvc.perform(get("/me/received-requests").header("Authorization", coachBearer)));
        String requestId = null;
        for (JsonNode r : inbox) {
            if (r.toString().contains("Instagram")) {
                requestId = r.get("id").asText();
            }
        }
        assertThat(requestId).isNotNull();
        mvc.perform(post("/me/received-requests/{id}/accept", requestId).header("Authorization", coachBearer))
                .andExpect(status().isOk());

        Athlete fiche = userRepository.findByEmailIgnoreCase(email).orElseThrow().getAthlete();
        assertThat(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(fiche.getId())
                .orElseThrow().getClub()).as("athlète privé du coach").isNull();
    }

    /** Adresse pas encore confirmée : le lien est retenu, et sert à la confirmation. */
    @Test
    void theLinkWaitsForTheEmailConfirmation() throws Exception {
        String token = createLink("{\"directEntry\":true}").get("token").asText();
        String email = "nora-" + UUID.randomUUID() + "@exemple.fr";
        String athlete = registerAthlete(email, false);

        JsonNode res = json(mvc.perform(post("/me/invites/{t}/join", token).header("Authorization", athlete))
                .andExpect(status().isOk()));
        assertThat(res.get("outcome").asText()).isEqualTo("VERIFY_EMAIL");

        String verifyToken = userRepository.findByEmailIgnoreCase(email).orElseThrow().getVerifyToken();
        JsonNode verified = json(mvc.perform(post("/public/verify-email/{t}", verifyToken)).andExpect(status().isOk()));
        assertThat(verified.get("invitedBy").asText()).isEqualTo(coach.getFullName());
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getAthlete().getClub().isPersonal())
                .isFalse();
    }

    /** Déjà suivi, lien désactivé : deux refus qui disent pourquoi. */
    @Test
    void refusalsSayWhy() throws Exception {
        JsonNode link = createLink("{\"directEntry\":true}");
        String token = link.get("token").asText();
        String athlete = registerAthlete("sam-" + UUID.randomUUID() + "@exemple.fr", true);
        mvc.perform(post("/me/invites/{t}/join", token).header("Authorization", athlete)).andExpect(status().isOk());
        mvc.perform(post("/me/invites/{t}/join", token).header("Authorization", athlete))
                .andExpect(status().isConflict());

        mvc.perform(delete("/me/invite-links/{id}", link.get("id").asText()).header("Authorization", coachBearer))
                .andExpect(status().isOk());
        String other = registerAthlete("ines-" + UUID.randomUUID() + "@exemple.fr", true);
        String body = mvc.perform(post("/me/invites/{t}/join", token).header("Authorization", other))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("désactivé");
    }

    private TrainingGroup newGroup(String name) {
        TrainingGroup g = new TrainingGroup();
        g.setClub(coach.getClub());
        g.setName(name);
        return groupRepository.saveAndFlush(g);
    }

    private JsonNode createLink(String body) throws Exception {
        return json(mvc.perform(post("/me/invite-links").header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().isCreated()));
    }

    private String registerAthlete(String email, boolean verified) throws Exception {
        JsonNode res = json(mvc.perform(post("/public/athlete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1234\","
                                + "\"firstName\":\"Lea\",\"lastName\":\"Martin\","
                                + "\"birthDate\":\"" + java.time.LocalDate.now().minusYears(28) + "\","
                                + "\"termsAccepted\":true,\"healthDataConsent\":true}"))
                .andExpect(status().isCreated()));
        if (verified) {
            User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            user.setEmailVerified(true);
            userRepository.saveAndFlush(user);
        }
        return "Bearer " + res.get("accessToken").asText();
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearer(String email) throws Exception {
        return "Bearer " + MfaTestSupport.complete(mvc, context, email, json(mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()))).get("accessToken").asText();
    }
}
