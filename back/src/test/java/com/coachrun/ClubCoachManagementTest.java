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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gestion des membres du club (⑨) : ajout d'un coach existant, retrait, et protection
 * du propriétaire. L'inscription crée bien le membre OWNER.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClubCoachManagementTest {

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

    private JsonNode members() throws Exception {
        return objectMapper.readTree(mvc.perform(get("/clubs/{c}/members", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void registrationCreatesOwnerMember() throws Exception {
        JsonNode reg = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner.test@darilab.app\",\"password\":\"password123\","
                                + "\"fullName\":\"Owner Test\",\"termsAccepted\":true,\"clubName\":\"Club Test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String newBearer = "Bearer " + reg.get("accessToken").asText();
        String newClubId = reg.get("user").get("clubId").asText();

        JsonNode m = objectMapper.readTree(mvc.perform(get("/clubs/{c}/members", newClubId)
                        .header("Authorization", newBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(m.size()).isEqualTo(1);
        assertThat(m.get(0).get("clubRole").asText()).isEqualTo("OWNER");
    }

    @Test
    void addAndRemoveExistingCoach() throws Exception {
        // Un coach avec son propre compte (autre club).
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"assistant2@darilab.app\",\"password\":\"password123\","
                                + "\"fullName\":\"Assistant Deux\",\"termsAccepted\":true,\"clubName\":\"Autre Club\"}"))
                .andExpect(status().isCreated());

        int before = members().size();
        JsonNode added = objectMapper.readTree(mvc.perform(post("/clubs/{c}/members", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"assistant2@darilab.app\",\"role\":\"COACH_ASSISTANT\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String coachId = added.get("coachId").asText();
        assertThat(members().size()).isEqualTo(before + 1);

        // Doublon refusé.
        mvc.perform(post("/clubs/{c}/members", clubId).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"assistant2@darilab.app\",\"role\":\"COACH_ASSISTANT\"}"))
                .andExpect(status().isConflict());

        // Retrait OK.
        mvc.perform(delete("/clubs/{c}/members/{id}", clubId, coachId).header("Authorization", bearer))
                .andExpect(status().isNoContent());
        assertThat(members().size()).isEqualTo(before);
    }

    /**
     * La composition du club est réservée au propriétaire.
     *
     * <p>Le modèle à trois rôles était documenté depuis le début mais n'était consulté par aucune
     * autorisation : le seul contrôle était l'appartenance au club. Dans un club à trois coachs,
     * un assistant pouvait donc éjecter le coach principal et ses collègues — en leur faisant
     * perdre l'accès à leurs propres athlètes.</p>
     */
    @Test
    void assistantCoachCannotManageClubMembership() throws Exception {
        JsonNode assistantAuth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoSeedService.COACH_EMAIL
                                + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String assistantBearer = "Bearer " + assistantAuth.get("accessToken").asText();

        String ownerId = null;
        for (JsonNode m : members()) {
            if ("OWNER".equals(m.get("clubRole").asText())) ownerId = m.get("coachId").asText();
        }
        assertThat(ownerId).isNotNull();

        // Ni retirer un collègue…
        mvc.perform(delete("/clubs/{c}/members/{id}", clubId, ownerId)
                        .header("Authorization", assistantBearer))
                .andExpect(status().isForbidden());

        // …ni en ajouter un.
        mvc.perform(post("/clubs/{c}/members", clubId).header("Authorization", assistantBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"intrus@darilab.app\",\"role\":\"COACH_ASSISTANT\"}"))
                .andExpect(status().isForbidden());

        // Il garde évidemment la lecture de la composition du club.
        mvc.perform(get("/clubs/{c}/members", clubId).header("Authorization", assistantBearer))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCannotBeRemoved() throws Exception {
        String ownerId = null;
        for (JsonNode m : members()) {
            if ("OWNER".equals(m.get("clubRole").asText())) ownerId = m.get("coachId").asText();
        }
        assertThat(ownerId).isNotNull();
        mvc.perform(delete("/clubs/{c}/members/{id}", clubId, ownerId).header("Authorization", bearer))
                .andExpect(status().isConflict());
    }
}
