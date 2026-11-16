package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.User;
import com.coachrun.repository.CoachProfileRepository;
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

/** Témoignages : l'athlète écrit sur son coach, le coach choisit de publier, la page publique l'affiche. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachTestimonialTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CoachProfileRepository profileRepository;

    private MockMvc mvc;
    private String coach;
    private String slug;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        demoSeedService.seedHub();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        coach = login(DemoSeedService.SOLO_COACH_EMAIL, DemoSeedService.DEMO_PASSWORD);
        User sarah = userRepository.findByEmailIgnoreCase(DemoSeedService.SOLO_COACH_EMAIL).orElseThrow();
        slug = profileRepository.findByCoachId(sarah.getId()).orElseThrow().getSlug();
    }

    @Test
    void writtenByTheAthletePublishedByTheCoach() throws Exception {
        String athlete = joinBy(coach, "{\"directEntry\":true}", "lea-" + UUID.randomUUID() + "@exemple.fr");
        put(athlete, "Trop court").andExpect(status().isConflict());
        String text = "Sarah m'a emmenée sous les 4 heures au marathon, avec un plan qui tenait compte de mon travail.";
        JsonNode mine = json(put(athlete, text).andExpect(status().isOk()));
        assertThat(mine.get("published").asBoolean()).isFalse();

        assertThat(json(mvc.perform(get("/public/coaches/{s}", slug))).get("testimonials").toString())
                .as("pas publié tant que le coach ne l'a pas choisi").doesNotContain("4 heures");

        JsonNode received = json(mvc.perform(get("/me/testimonials").header("Authorization", coach))
                .andExpect(status().isOk()));
        String id = null;
        for (JsonNode t : received) {
            if (t.get("body").asText().contains("4 heures")) {
                id = t.get("id").asText();
                assertThat(t.get("authorName").asText()).isEqualTo("Lea M.");
            }
        }
        mvc.perform(post("/me/testimonials/{id}/publish", id).header("Authorization", coach))
                .andExpect(status().isOk());
        assertThat(json(mvc.perform(get("/public/coaches/{s}", slug))).get("testimonials").toString())
                .contains("4 heures").contains("Lea M.");

        // Le reprendre le dépublie ; le retirer l'efface.
        put(athlete, text + " Merci !").andExpect(status().isOk());
        assertThat(json(mvc.perform(get("/public/coaches/{s}", slug))).get("testimonials").toString())
                .doesNotContain("4 heures");
        mvc.perform(delete("/me/coach/testimonial").header("Authorization", athlete)).andExpect(status().isNoContent());
        mvc.perform(get("/me/coach/testimonial").header("Authorization", athlete)).andExpect(status().isNoContent());
    }

    @Test
    void onlyACoachedAthleteCanWriteOne() throws Exception {
        String email = "solo-" + UUID.randomUUID() + "@exemple.fr";
        mvc.perform(post("/public/athlete-registration")
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1234\","
                                + "\"firstName\":\"Tom\",\"lastName\":\"Martin\","
                                + "\"birthDate\":\"" + java.time.LocalDate.now().minusYears(28) + "\","
                                + "\"termsAccepted\":true,\"healthDataConsent\":true}"))
                .andExpect(status().isCreated());
        String athlete = login(email, "password1234");
        put(athlete, "Un excellent coach que je n'ai pourtant jamais eu, croyez-moi.").andExpect(status().isConflict());
    }

    private ResultActions put(String athlete, String text) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/me/coach/testimonial").header("Authorization", athlete)
                .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                .content(objectMapper.writeValueAsString(java.util.Map.of("body", text))));
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
