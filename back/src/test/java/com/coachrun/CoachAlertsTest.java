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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * File d'alertes du tableau de bord coach (Chantier 3) : un athlète avec plusieurs séances
 * passées non réalisées doit remonter une alerte « séances manquées » critique.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachAlertsTest {

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
    void missedSessionsRaiseCriticalAlert() throws Exception {
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Léa\",\"lastName\":\"Manquée\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // 3 séances passées laissées PLANIFIÉES ⇒ « manquées ».
        for (int daysAgo : new int[]{3, 5, 7}) {
            mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                            .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"scheduledDate\":\"" + LocalDate.now().minusDays(daysAgo)
                                    + "\",\"type\":\"ENDURANCE\",\"title\":\"Footing\"}"))
                    .andExpect(status().isCreated());
        }

        JsonNode alerts = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/dashboard/alerts", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        boolean missedRed = false;
        for (JsonNode al : alerts) {
            if (athleteId.equals(al.get("athleteId").asText())
                    && "MISSED".equals(al.get("type").asText())) {
                assertThat(al.get("severity").asText()).isEqualTo("RED");
                missedRed = true;
            }
        }
        assertThat(missedRed).as("alerte « séances manquées » critique pour l'athlète").isTrue();

        // Tri par gravité : la première alerte est rouge.
        if (alerts.size() > 0) {
            assertThat(alerts.get(0).get("severity").asText()).isEqualTo("RED");
        }
    }
}
