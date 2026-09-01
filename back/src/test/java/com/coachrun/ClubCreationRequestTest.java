package com.coachrun;

import com.coachrun.controller.PublicRegistrationController;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.repository.ClubCreationRequestRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.security.JwtService;
import com.coachrun.service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le régime « sur demande » : le formulaire public dépose une demande, l'administrateur tranche,
 * et c'est la validation qui ouvre le club.
 *
 * <p><b>Pourquoi le mode est posé par réflexion</b> plutôt que par
 * {@code @SpringBootTest(properties = …)} : cette annotation forkerait un second contexte
 * applicatif, qui rejouerait Liquibase sur la base H2 partagée du profil de test (même raison que
 * le jeton Strava, posé dans {@code application-test.yml}). Le mode est restauré après chaque
 * test — les autres suites s'inscrivent en mode « open » et doivent continuer à le faire.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ClubCreationRequestTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ClubCreationRequestRepository requestRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PublicRegistrationController publicRegistrationController;
    @Autowired
    private AuthService authService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void setMode(String mode) {
        ReflectionTestUtils.setField(publicRegistrationController, "registrationMode", mode);
        ReflectionTestUtils.setField(authService, "registrationMode", mode);
    }

    @AfterEach
    void restoreOpenRegistration() {
        setMode("open");
    }

    private String adminToken() {
        User admin = new User();
        admin.setEmail("admin-" + UUID.randomUUID() + "@test.fr");
        admin.setFullName("Admin Test");
        admin.setRole(UserRole.PLATFORM_ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin = userRepository.save(admin);
        return jwtService.generateAccessToken(admin);
    }

    private String submission(String email, String clubName) {
        return """
                {"email":"%s","fullName":"Camille Roy","clubName":"%s",
                 "phone":"0600000000","message":"Club de 40 coureurs","termsAccepted":true}
                """.formatted(email, clubName);
    }

    /** Le front doit savoir quel formulaire montrer avant que le candidat n'ait rien saisi. */
    @Test
    void theRegistrationModeIsReadableWithoutAnAccount() throws Exception {
        setMode("request");
        mockMvc().perform(get("/public/registration-mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("REQUEST"));
    }

    /**
     * Le chemin complet : dépôt anonyme, arbitrage, ouverture du club.
     *
     * <p>Ce qui compte à la fin n'est pas que la demande porte « validée », mais qu'un compte
     * existe et qu'un lien permette d'y entrer : c'est tout ce que le coach verra.</p>
     */
    @Test
    void anApprovedRequestOpensTheClubAndItsCoachAccount() throws Exception {
        setMode("request");
        MockMvc mvc = mockMvc();
        String email = "demande-" + UUID.randomUUID() + "@test.fr";

        mvc.perform(post("/public/club-requests")
                        .contentType(MediaType.APPLICATION_JSON).content(submission(email, "Les Foulées")))
                .andExpect(status().isAccepted());

        // Rien n'est créé au dépôt : c'est tout l'objet du régime.
        assertThat(userRepository.existsByEmailIgnoreCase(email)).isFalse();

        String token = adminToken();
        JsonNode pending = objectMapper.readTree(mvc.perform(
                        get("/admin/club-requests?status=PENDING")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode mine = null;
        for (JsonNode node : pending.get("content")) {
            if (email.equals(node.get("email").asText())) {
                mine = node;
            }
        }
        assertThat(mine).as("la demande déposée doit apparaître dans la file").isNotNull();

        JsonNode approval = objectMapper.readTree(mvc.perform(
                        post("/admin/club-requests/" + mine.get("id").asText() + "/approve")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(approval.get("request").get("status").asText()).isEqualTo("APPROVED");
        assertThat(approval.get("request").get("createdClubId").asText()).isNotBlank();
        // Le lien est rendu à l'administrateur : sans envoi d'e-mail actif (profil de test), il
        // reste le seul moyen de débloquer le coach qu'on vient d'accepter.
        assertThat(approval.get("activationUrl").asText()).contains("/reset-password/");
        assertThat(userRepository.existsByEmailIgnoreCase(email)).isTrue();
    }

    /** Le lien de validation vaut mot de passe : il doit ouvrir une session utilisable. */
    @Test
    void theActivationLinkLetsTheCoachChooseAPasswordAndEnter() throws Exception {
        setMode("request");
        MockMvc mvc = mockMvc();
        String email = "activation-" + UUID.randomUUID() + "@test.fr";

        mvc.perform(post("/public/club-requests")
                        .contentType(MediaType.APPLICATION_JSON).content(submission(email, "Club Activation")))
                .andExpect(status().isAccepted());
        String token = adminToken();
        UUID requestId = requestRepository.findFirstByEmailIgnoreCaseAndStatus(
                email, com.coachrun.entity.enums.ClubRequestStatus.PENDING).orElseThrow().getId();

        JsonNode approval = objectMapper.readTree(mvc.perform(
                        post("/admin/club-requests/" + requestId + "/approve")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString());
        String activationUrl = approval.get("activationUrl").asText();
        String activationToken = activationUrl.substring(activationUrl.lastIndexOf('/') + 1);

        mvc.perform(post("/public/password-reset/" + activationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"motdepasse123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.role").value("HEAD_COACH"));
    }

    /** Un refus se relit : la demande reste en base, avec son motif. */
    @Test
    void aRejectedRequestKeepsItsReasonAndCreatesNothing() throws Exception {
        setMode("request");
        MockMvc mvc = mockMvc();
        String email = "refus-" + UUID.randomUUID() + "@test.fr";

        mvc.perform(post("/public/club-requests")
                        .contentType(MediaType.APPLICATION_JSON).content(submission(email, "Club Refusé")))
                .andExpect(status().isAccepted());
        UUID requestId = requestRepository.findFirstByEmailIgnoreCaseAndStatus(
                email, com.coachrun.entity.enums.ClubRequestStatus.PENDING).orElseThrow().getId();

        mvc.perform(post("/admin/club-requests/" + requestId + "/reject")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Structure hors du périmètre de la bêta.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote").value("Structure hors du périmètre de la bêta."));

        assertThat(userRepository.existsByEmailIgnoreCase(email)).isFalse();
    }

    /**
     * Deux administrateurs peuvent ouvrir la même file. Le second doit lire « déjà traitée »,
     * et non ouvrir un second club pour le même candidat.
     */
    @Test
    void aRequestCannotBeArbitratedTwice() throws Exception {
        setMode("request");
        MockMvc mvc = mockMvc();
        String email = "double-" + UUID.randomUUID() + "@test.fr";
        String token = adminToken();

        mvc.perform(post("/public/club-requests")
                        .contentType(MediaType.APPLICATION_JSON).content(submission(email, "Club Double")))
                .andExpect(status().isAccepted());
        UUID requestId = requestRepository.findFirstByEmailIgnoreCaseAndStatus(
                email, com.coachrun.entity.enums.ClubRequestStatus.PENDING).orElseThrow().getId();

        mvc.perform(post("/admin/club-requests/" + requestId + "/approve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/admin/club-requests/" + requestId + "/approve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    /** Renvoyer le formulaire faute de nouvelle est normal : on ne veut pas dix lignes identiques. */
    @Test
    void aSecondSubmissionForTheSameAddressIsRefusedWithAnExplanation() throws Exception {
        setMode("request");
        MockMvc mvc = mockMvc();
        String email = "doublon-" + UUID.randomUUID() + "@test.fr";

        mvc.perform(post("/public/club-requests")
                        .contentType(MediaType.APPLICATION_JSON).content(submission(email, "Club A")))
                .andExpect(status().isAccepted());
        mvc.perform(post("/public/club-requests")
                        .contentType(MediaType.APPLICATION_JSON).content(submission(email, "Club A")))
                .andExpect(status().isConflict());
    }

    /**
     * En régime « sur demande », l'inscription directe est fermée — et le refus doit nommer le
     * chemin à suivre, pas se contenter d'un « accès refusé ».
     */
    @Test
    void directRegistrationIsClosedInRequestMode() throws Exception {
        setMode("request");
        String body = """
                {"email":"direct-%s@test.fr","password":"password123","fullName":"C",
                 "termsAccepted":true,"clubName":"Club Direct"}
                """.formatted(UUID.randomUUID());
        mockMvc().perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Créer mon club")));
    }

    /** Hors de ce régime, la file ne doit pas se remplir de demandes que personne ne regarde. */
    @Test
    void theRequestFormIsClosedWhenRegistrationIsOpen() throws Exception {
        setMode("open");
        mockMvc().perform(post("/public/club-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submission("hors-regime-" + UUID.randomUUID() + "@test.fr", "Club X")))
                .andExpect(status().isForbidden());
    }
}
