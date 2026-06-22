package com.coachrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AthletePortalTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void magicLinkOnboardingAndTodayFeedback() throws Exception {
        MockMvc mvc = mockMvc();
        // coach + athlète + séance du jour
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"p-%s@test.fr","password":"password123","fullName":"C","clubName":"PC %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String coachToken = auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();

        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + coachToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Lea\",\"lastName\":\"Run\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        String today = LocalDate.now().toString();
        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + coachToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"" + today + "\",\"type\":\"ENDURANCE\",\"title\":\"Footing\"}"))
                .andExpect(status().isCreated());

        // invitation + récupération du token via le lien
        String inviteUrl = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/invitation", clubId, athleteId)
                        .header("Authorization", "Bearer " + coachToken))
                .andReturn().getResponse().getContentAsString()).get("inviteUrl").asText();
        String inviteToken = inviteUrl.substring(inviteUrl.lastIndexOf('/') + 1);

        // acceptation → compte ATHLETE + jetons
        JsonNode athAuth = objectMapper.readTree(mvc.perform(post("/public/invitations/{t}/accept", inviteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ATHLETE"))
                .andReturn().getResponse().getContentAsString());
        String athToken = athAuth.get("accessToken").asText();

        // séance du jour visible
        String wId = objectMapper.readTree(mvc.perform(get("/me/today")
                        .header("Authorization", "Bearer " + athToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asText();

        // feedback RPE + statut réalisé
        mvc.perform(patch("/me/workouts/{w}/feedback", wId)
                        .header("Authorization", "Bearer " + athToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"rpe\":7,\"comment\":\"Bonnes sensations\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.rpe").value(7));

        // un athlète ne peut PAS accéder aux routes coach du club
        mvc.perform(get("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + athToken))
                .andExpect(status().isForbidden());

        // RGPD — export de ses données
        mvc.perform(get("/me/export").header("Authorization", "Bearer " + athToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.firstName").value("Lea"))
                .andExpect(jsonPath("$.workouts").isArray());

        // RGPD — droit à l'oubli
        mvc.perform(delete("/me").header("Authorization", "Bearer " + athToken))
                .andExpect(status().isNoContent());
    }
}
