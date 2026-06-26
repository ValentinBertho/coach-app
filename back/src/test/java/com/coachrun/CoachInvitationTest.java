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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invitation d'un coach sans compte : création d'un compte en attente + lien, acceptation
 * (mot de passe) puis connexion. Complète la gestion multi-coach (⑨).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachInvitationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String bearer;
    private String clubId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoSeedService.HEAD_COACH_EMAIL
                                + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        bearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();
    }

    @Test
    void inviteNewCoachThenAcceptAndLogin() throws Exception {
        JsonNode res = objectMapper.readTree(mvc.perform(post("/clubs/{c}/members", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new.coach@darilab.app\",\"role\":\"COACH_ASSISTANT\","
                                + "\"fullName\":\"Nouveau Coach\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(res.get("invited").asBoolean()).isTrue();
        String inviteUrl = res.get("inviteUrl").asText();
        assertThat(inviteUrl).contains("/coach-invitation/");
        String token = inviteUrl.substring(inviteUrl.lastIndexOf('/') + 1);

        // Le membre apparaît « en attente ».
        JsonNode members = objectMapper.readTree(mvc.perform(get("/clubs/{c}/members", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        boolean pendingFound = false;
        for (JsonNode m : members) {
            if ("Nouveau Coach".equals(m.get("name").asText())) {
                pendingFound = m.get("pending").asBoolean();
            }
        }
        assertThat(pendingFound).isTrue();

        // Infos publiques de l'invitation.
        JsonNode info = objectMapper.readTree(mvc.perform(get("/public/coach-invitations/{t}", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(info.get("email").asText()).isEqualTo("new.coach@darilab.app");
        assertThat(info.get("clubName").asText()).isNotEmpty();

        // Acceptation (définition du mot de passe) → session.
        JsonNode accepted = objectMapper.readTree(mvc.perform(
                        post("/public/coach-invitations/{t}/accept", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(accepted.get("accessToken").asText()).isNotEmpty();

        // Le compte est désormais actif : connexion possible.
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new.coach@darilab.app\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        // Le lien d'invitation n'est plus valide.
        mvc.perform(get("/public/coach-invitations/{t}", token)).andExpect(status().isNotFound());
    }
}
