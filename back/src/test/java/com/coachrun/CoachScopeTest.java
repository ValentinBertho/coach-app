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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * « Mes athlètes », pour un coach qui n'a créé personne.
 *
 * <p><b>Le défaut.</b> Le périmètre ne regardait que les relations coach↔athlète, c'est-à-dire les
 * fiches que le coach avait lui-même ouvertes. Un coach principal arrivant dans un club dont le
 * propriétaire a saisi les athlètes trouvait donc « Mes athlètes » <b>vide</b> — alors qu'il a
 * l'écriture sur tout le club. Écrire à quelqu'un n'est pas la même chose que le suivre : c'est
 * l'écriture qui définit « les miens », pas la propriété de la fiche.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachScopeTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String ownerBearer;
    private String clubId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode owner = login(DemoSeedService.HEAD_COACH_EMAIL);
        ownerBearer = "Bearer " + owner.get("accessToken").asText();
        clubId = owner.get("user").get("clubId").asText();
    }

    /** Un coach principal sans aucune fiche à son nom voit les athlètes du club. */
    @Test
    void aPrincipalCoachSeesTheClubAthletesInHisOwnScope() throws Exception {
        String newcomer = secondCoachBearer("COACH_PRINCIPAL");

        assertThat(athleteCount(newcomer, "mine"))
                .as("« Mes athletes » etait vide pour qui n'avait cree personne")
                .isGreaterThan(0);
    }

    /**
     * L'assistant, lui, ne voit rien tant qu'on ne lui a rien confié : son rôle est justement de
     * n'avoir accès qu'aux athlètes qu'on lui assigne.
     */
    @Test
    void anAssistantSeesNothingUntilSomethingIsEntrusted() throws Exception {
        String assistant = secondCoachBearer("COACH_ASSISTANT");

        assertThat(athleteCount(assistant, "mine")).isZero();
    }

    /** Et « tout le club » reste ce qu'il était : ce que le coach a le droit de voir. */
    @Test
    void theWholeClubScopeStillShowsWhatIsReadable() throws Exception {
        assertThat(athleteCount(ownerBearer, "all")).isGreaterThan(0);
    }

    // --- Utilitaires ---------------------------------------------------------------------------

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String secondCoachBearer(String role) throws Exception {
        JsonNode invite = objectMapper.readTree(mvc.perform(post("/clubs/{c}/members", clubId)
                        .header("Authorization", ownerBearer).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "nouveau.coach@darilab.app",
                                "role", role,
                                "fullName", "Nouveau Coach"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String url = invite.get("inviteUrl").asText();
        String token = url.substring(url.lastIndexOf('/') + 1);
        return "Bearer " + objectMapper.readTree(mvc.perform(
                        post("/public/coach-invitations/{t}/accept", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"password123\",\"termsAccepted\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    /** Athlètes rendus par le tableau de forme pour ce périmètre (route + trail). */
    private int athleteCount(String bearer, String scope) throws Exception {
        JsonNode body = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/dashboard/form", clubId).param("scope", scope)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return body.get("routeAthletes").size() + body.get("trailAthletes").size();
    }
}
