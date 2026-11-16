package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.User;
import com.coachrun.repository.ClubMemberRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le départ d'un coach du club, et l'athlète qui quitte le club en gardant son coach.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClubDepartureTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CoachAthleteRelationRepository relationRepository;
    @Autowired private ClubMemberRepository memberRepository;

    private MockMvc mvc;
    private String owner;
    private String assistant;
    private User ownerUser;
    private User assistantUser;
    private UUID clubId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        owner = login(DemoSeedService.HEAD_COACH_EMAIL, DemoSeedService.DEMO_PASSWORD);
        assistant = login(DemoSeedService.COACH_EMAIL, DemoSeedService.DEMO_PASSWORD);
        ownerUser = userRepository.findByEmailIgnoreCase(DemoSeedService.HEAD_COACH_EMAIL).orElseThrow();
        assistantUser = userRepository.findByEmailIgnoreCase(DemoSeedService.COACH_EMAIL).orElseThrow();
        clubId = ownerUser.getClub().getId();
    }

    /** Retiré par le propriétaire : l'athlète du club reste au club, l'athlète privé suit son coach. */
    @Test
    void clubAthletesStayAndPrivateAthletesFollowTheCoach() throws Exception {
        String clubEmail = "club-" + UUID.randomUUID() + "@exemple.fr";
        joinBy(assistant, "{\"directEntry\":true,\"clubMembership\":true}", clubEmail);
        String privateEmail = "prive-" + UUID.randomUUID() + "@exemple.fr";
        joinBy(assistant, "{\"directEntry\":true,\"clubMembership\":false}", privateEmail);
        UUID clubFiche = fiche(clubEmail).getId();
        UUID privateFiche = fiche(privateEmail).getId();

        JsonNode impact = json(mvc.perform(get("/clubs/{c}/members/{m}/departure", clubId, assistantUser.getId())
                .header("Authorization", owner)).andExpect(status().isOk()));
        assertThat(impact.get("followers").toString()).contains(privateFiche.toString());
        assertThat(impact.get("handedOver").toString()).contains(clubFiche.toString());

        mvc.perform(delete("/clubs/{c}/members/{m}", clubId, assistantUser.getId()).header("Authorization", owner))
                .andExpect(status().isOk());

        CoachAthleteRelation clubRelation = relationRepository
                .findByAthleteIdAndReferentTrueAndActiveTrue(clubFiche).orElseThrow();
        assertThat(clubRelation.getCoach().getId()).as("le propriétaire reprend").isEqualTo(ownerUser.getId());
        assertThat(clubRelation.getClub()).isNotNull();

        assertThat(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(privateFiche)).isEmpty();
        Athlete followed = fiche(privateEmail);
        assertThat(followed.getId()).isNotEqualTo(privateFiche);
        assertThat(followed.getClub().getId()).as("dans l'espace du coach").isNotEqualTo(clubId);
        assertThat(followed.getClub().isSoloPractice()).isTrue();
        assertThat(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(followed.getId()).orElseThrow()
                .getCoach().getId()).isEqualTo(assistantUser.getId());

        User coach = userRepository.findById(assistantUser.getId()).orElseThrow();
        assertThat(coach.getClub().getId()).isEqualTo(followed.getClub().getId());
        assertThat(memberRepository.findByClubIdAndCoachIdAndActiveTrue(clubId, coach.getId())).isEmpty();
    }

    /** Le propriétaire ne quitte pas son club sans l'avoir confié. */
    @Test
    void theOwnerCannotLeave() throws Exception {
        mvc.perform(post("/clubs/{c}/leave", clubId).header("Authorization", owner))
                .andExpect(status().isConflict());
    }

    /** L'athlète quitte le club et garde son coach, en athlète privé. */
    @Test
    void anAthleteLeavesTheClubButKeepsTheCoach() throws Exception {
        String email = "lea-" + UUID.randomUUID() + "@exemple.fr";
        String athlete = joinBy(owner, "{\"directEntry\":true,\"clubMembership\":true}", email);
        JsonNode me = json(mvc.perform(get("/me/coach").header("Authorization", athlete)).andExpect(status().isOk()));
        assertThat(me.get("clubName").isNull()).isFalse();

        mvc.perform(post("/me/coach/leave-club").header("Authorization", athlete)).andExpect(status().isNoContent());
        CoachAthleteRelation r = relationRepository
                .findByAthleteIdAndReferentTrueAndActiveTrue(fiche(email).getId()).orElseThrow();
        assertThat(r.getClub()).isNull();
        assertThat(r.getCoach().getId()).isEqualTo(ownerUser.getId());
        mvc.perform(post("/me/coach/leave-club").header("Authorization", athlete)).andExpect(status().isConflict());
    }

    /** Crée un athlète vérifié qui rejoint le coach par un lien ; rend son jeton après l'entrée. */
    private String joinBy(String coach, String linkBody, String email) throws Exception {
        String token = json(mvc.perform(post("/me/invite-links").header("Authorization", coach)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(linkBody))
                .andExpect(status().isCreated())).get("token").asText();
        mvc.perform(post("/public/athlete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1234\","
                                + "\"firstName\":\"Lea\",\"lastName\":\"Martin\","
                                + "\"birthDate\":\"" + java.time.LocalDate.now().minusYears(28) + "\","
                                + "\"termsAccepted\":true,\"healthDataConsent\":true}"))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
        String athlete = login(email, "password1234");
        mvc.perform(post("/me/invites/{t}/join", token).header("Authorization", athlete)).andExpect(status().isOk());
        return login(email, "password1234");
    }

    private Athlete fiche(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getAthlete();
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
