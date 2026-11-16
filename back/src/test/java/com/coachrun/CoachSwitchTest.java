package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.User;
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
