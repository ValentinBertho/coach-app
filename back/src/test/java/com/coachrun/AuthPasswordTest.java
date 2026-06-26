package com.coachrun;

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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentification de bout en bout : l'athlète définit un mot de passe à l'acceptation et peut
 * se reconnecter ; un coach peut réinitialiser son mot de passe oublié.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthPasswordTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    private MockMvc mvc;
    private String bearer;
    private String clubId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode auth = login(DemoSeedService.HEAD_COACH_EMAIL, DemoSeedService.DEMO_PASSWORD);
        bearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();
    }

    private JsonNode login(String email, String password) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void athleteSetsPasswordAtAcceptanceThenLogsIn() throws Exception {
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Léa\",\"lastName\":\"Connect\",\"email\":\"lea.connect@darilab.app\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        String url = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/invitation", clubId, athleteId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("inviteUrl").asText();
        String token = url.substring(url.lastIndexOf('/') + 1);

        mvc.perform(post("/public/invitations/{t}/accept", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"healthDataConsent\":true,\"password\":\"athletepass1\"}"))
                .andExpect(status().isOk());

        // Reconnexion avec e-mail + mot de passe.
        JsonNode session = login("lea.connect@darilab.app", "athletepass1");
        assertThat(session.get("user").get("role").asText()).isEqualTo("ATHLETE");
    }

    @Test
    void coachResetsForgottenPassword() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset.coach@darilab.app\",\"password\":\"oldpassword1\","
                                + "\"fullName\":\"Reset Coach\",\"clubName\":\"Reset Club\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/public/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset.coach@darilab.app\"}"))
                .andExpect(status().isOk());

        String token = userRepository.findByEmailIgnoreCase("reset.coach@darilab.app").orElseThrow().getResetToken();
        assertThat(token).isNotNull();

        mvc.perform(get("/public/password-reset/{t}", token)).andExpect(status().isOk());

        mvc.perform(post("/public/password-reset/{t}", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"newpassword1\"}"))
                .andExpect(status().isOk());

        // Nouveau mot de passe OK, ancien refusé, lien à usage unique.
        login("reset.coach@darilab.app", "newpassword1");
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset.coach@darilab.app\",\"password\":\"oldpassword1\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/public/password-reset/{t}", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"another12\"}"))
                .andExpect(status().isNotFound());
    }
}
