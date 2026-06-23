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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Multi-coach / club : membres, permissions graduées et bascule privé/club (cf. DARI Lab §4).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClubMembershipTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String coachBearer;
    private String clubId;
    private String athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        JsonNode coach = login(DemoSeedService.HEAD_COACH_EMAIL);
        coachBearer = "Bearer " + coach.get("accessToken").asText();
        clubId = coach.get("user").get("clubId").asText();
        athleteId = login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText();
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void membersPermissionsAndOwnership() throws Exception {
        // Membres : owner + assistant.
        JsonNode members = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/members", clubId).header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(members.size()).isGreaterThanOrEqualTo(2);
        String assistantId = null;
        for (JsonNode m : members) {
            if ("COACH_ASSISTANT".equals(m.get("clubRole").asText())) {
                assistantId = m.get("coachId").asText();
            }
        }
        assertThat(assistantId).isNotNull();

        // Accès de l'athlète démo : club + permission lecture pour l'assistant (seed).
        JsonNode access = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/access", clubId, athleteId).header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(access.get("ownership").asText()).isEqualTo("CLUB");
        assertThat(access.get("permissions")).isNotEmpty();

        // Élever la permission de l'assistant à WRITE.
        JsonNode granted = objectMapper.readTree(mvc.perform(
                        put("/clubs/{c}/athletes/{a}/permissions/{co}", clubId, athleteId, assistantId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"permission\":\"WRITE\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        boolean hasWrite = false;
        for (JsonNode p : granted.get("permissions")) {
            if (assistantId.equals(p.get("coachId").asText()) && "WRITE".equals(p.get("permission").asText())) {
                hasWrite = true;
            }
        }
        assertThat(hasWrite).isTrue();

        // Passage en privé refusé tant qu'une permission active existe.
        mvc.perform(patch("/clubs/{c}/athletes/{a}/ownership", clubId, athleteId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownership\":\"PRIVATE\"}"))
                .andExpect(status().isConflict());

        // Révoquer puis basculer en privé.
        mvc.perform(delete("/clubs/{c}/athletes/{a}/permissions/{co}", clubId, athleteId, assistantId)
                .header("Authorization", coachBearer)).andExpect(status().isOk());
        JsonNode priv = objectMapper.readTree(mvc.perform(
                        patch("/clubs/{c}/athletes/{a}/ownership", clubId, athleteId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"ownership\":\"PRIVATE\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(priv.get("ownership").asText()).isEqualTo("PRIVATE");
        assertThat(priv.get("permissions")).isEmpty();
    }
}
