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

        // Les CGU ne sont pas optionnelles : la preuve de consentement RGPD ne peut pas dépendre
        // du client qui pense à envoyer le champ.
        mvc.perform(post("/public/coach-invitations/{t}/accept", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/public/coach-invitations/{t}/accept", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\",\"termsAccepted\":false}"))
                .andExpect(status().isBadRequest());
        // Et un mot de passe trop court est refusé, comme à l'inscription.
        mvc.perform(post("/public/coach-invitations/{t}/accept", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"court\",\"termsAccepted\":true}"))
                .andExpect(status().isBadRequest());

        // Acceptation (définition du mot de passe) → session.
        JsonNode accepted = objectMapper.readTree(mvc.perform(
                        post("/public/coach-invitations/{t}/accept", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"password123\",\"termsAccepted\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(accepted.get("accessToken").asText()).isNotEmpty();

        // Le compte est désormais actif : connexion possible.
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new.coach@darilab.app\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        // Le lien d'invitation n'est plus valide.
        mvc.perform(get("/public/coach-invitations/{t}", token)).andExpect(status().isNotFound());
    }

    /**
     * Renvoyer l'invitation : le cas le plus banal du club — l'e-mail s'est perdu, ou le lien a
     * expiré au bout de quatorze jours.
     *
     * <p>Le club n'avait alors aucune issue : l'adresse existant déjà, « Ajouter / inviter »
     * répondait « ce coach est déjà membre », et rien ne permettait de renvoyer le message. Il
     * fallait retirer le coach pour le réinviter.</p>
     */
    @Test
    void theInvitationCanBeSentAgain() throws Exception {
        String firstToken = tokenOf(invite("perdu@darilab.app", "Coach Perdu"));

        JsonNode again = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/members/{coach}/resend-invite", clubId, coachIdOf("Coach Perdu"))
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        String secondToken = tokenOf(again);
        assertThat(secondToken).as("un nouveau lien, donc un nouveau délai").isNotEqualTo(firstToken);

        // Le lien précédent ne doit plus ouvrir de porte : il traîne peut-être dans une boîte.
        mvc.perform(get("/public/coach-invitations/{t}", firstToken)).andExpect(status().isNotFound());
        mvc.perform(get("/public/coach-invitations/{t}", secondToken)).andExpect(status().isOk());
    }

    /** Un coach déjà actif ne se réinvite pas : il se connecte, ou refait son mot de passe. */
    @Test
    void resendingToAnAlreadyActiveCoachIsRefused() throws Exception {
        String token = tokenOf(invite("actif@darilab.app", "Coach Actif"));
        mvc.perform(post("/public/coach-invitations/{t}/accept", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\",\"termsAccepted\":true}"))
                .andExpect(status().isOk());

        mvc.perform(post("/clubs/{c}/members/{coach}/resend-invite", clubId, coachIdOf("Coach Actif"))
                        .header("Authorization", bearer))
                .andExpect(status().isConflict());
    }

    // --- Utilitaires --------------------------------------------------------------------------

    private JsonNode invite(String email, String fullName) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/clubs/{c}/members", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email, "role", "COACH_ASSISTANT", "fullName", fullName))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /** Le jeton porté par le lien d'invitation. */
    private String tokenOf(JsonNode inviteResponse) {
        String url = inviteResponse.get("inviteUrl").asText();
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private String coachIdOf(String fullName) throws Exception {
        JsonNode members = objectMapper.readTree(mvc.perform(get("/clubs/{c}/members", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode m : members) {
            if (fullName.equals(m.get("name").asText())) {
                return m.get("coachId").asText();
            }
        }
        throw new AssertionError("Coach absent du club : " + fullName);
    }
}
