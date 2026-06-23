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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tableau de bord coach « état de forme » : répartition Route/Trail et pastille de forme.
 * Crée son propre athlète (déterministe, indépendant du jeu de démo).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachFormDashboardTest {

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
    void formDashboardSplitsByDisciplineAndShowsFormPill() throws Exception {
        // Athlète dédié au test, mis en Trail.
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Kilian\",\"lastName\":\"TrailTest\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        mvc.perform(put("/clubs/{c}/athletes/{a}/physio", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discipline\":\"TRAIL\"}"))
                .andExpect(status().isOk());

        JsonNode dash = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/dashboard/form", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(dash.get("total").asInt()).isEqualTo(
                dash.get("route").asInt() + dash.get("trail").asInt());
        assertThat(dash.get("trail").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode mine = null;
        for (JsonNode row : dash.get("trailAthletes")) {
            if (athleteId.equals(row.get("id").asText())) {
                mine = row;
            }
        }
        assertThat(mine).isNotNull();
        // Sans retour de séance : athlète frais → pastille verte.
        assertThat(mine.get("formStatus").asText()).isEqualTo("GREEN");
        assertThat(mine.get("discipline").asText()).isEqualTo("TRAIL");
    }
}
