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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Impersonation : un administrateur voit l'application comme un utilisateur donné.
 *
 * <p>La quasi-totalité des défauts remontés en bêta se décrivent par un écran, et ne se
 * reproduisent que depuis le compte concerné. Ces tests fixent ce qui borne ce pouvoir — car c'en
 * est un : accéder aux données de santé d'un athlète sans son mot de passe.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ImpersonationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private com.coachrun.repository.UserRepository userRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String bearer(String email) throws Exception {
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return "Bearer " + auth.get("accessToken").asText();
    }

    private String userIdOf(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getId().toString();
    }

    /**
     * Le cœur du besoin : le jeton rendu ouvre bien les écrans de l'athlète, que le compte
     * d'administration ne peut pas voir autrement.
     */
    @Test
    void unAdministrateurVoitLApplicationCommeLAthlete() throws Exception {
        String admin = bearer(DemoSeedService.ADMIN_EMAIL);

        JsonNode session = objectMapper.readTree(mvc.perform(
                        post("/admin/users/{id}/impersonate", userIdOf(DemoSeedService.ATHLETE_EMAIL))
                                .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ATHLETE"))
                .andReturn().getResponse().getContentAsString());

        // Pas de jeton de rafraîchissement : l'impersonation expire, elle ne se prolonge pas.
        assertThat(session.has("refreshToken")).isFalse();

        String borrowed = "Bearer " + session.get("accessToken").asText();
        mvc.perform(get("/me/today").header("Authorization", borrowed))
                .andExpect(status().isOk());
    }

    /**
     * Un compte d'administration ne s'emprunte pas : l'impersonation sert à voir l'application
     * comme un coach ou un athlète, pas à agir sous l'identité d'un pair — et un journal où deux
     * administrateurs s'empruntent mutuellement ne se lit plus.
     */
    @Test
    void unAdministrateurNeSEmprunteJamais() throws Exception {
        String admin = bearer(DemoSeedService.ADMIN_EMAIL);

        mvc.perform(post("/admin/users/{id}/impersonate", userIdOf(DemoSeedService.ADMIN_EMAIL))
                        .header("Authorization", admin))
                .andExpect(status().isForbidden());
    }

    /** Le pouvoir est réservé à l'administration : un coach ne l'obtient pas sur ses athlètes. */
    @Test
    void unCoachNePeutPasEmprunterUnCompte() throws Exception {
        String coach = bearer(DemoSeedService.HEAD_COACH_EMAIL);

        mvc.perform(post("/admin/users/{id}/impersonate", userIdOf(DemoSeedService.ATHLETE_EMAIL))
                        .header("Authorization", coach))
                .andExpect(status().isForbidden());
    }

    /** Sans jeton du tout, la route n'existe pas plus que les autres routes d'administration. */
    @Test
    void unAnonymeNObtientRien() throws Exception {
        mvc.perform(post("/admin/users/{id}/impersonate", userIdOf(DemoSeedService.ATHLETE_EMAIL)))
                .andExpect(status().isUnauthorized());
    }
}
