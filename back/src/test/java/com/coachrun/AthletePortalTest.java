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
    @Autowired
    private com.coachrun.repository.UserRepository userRepository;
    @Autowired
    private com.coachrun.service.ClockService clock;

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
                                {"email":"p-%s@test.fr","password":"password123","fullName":"C","termsAccepted": true, "clubName":"PC %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String coachToken = auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();
        verifyCoachEmail(clubId);

        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + coachToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Lea\",\"lastName\":\"Run\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // Date du jour dans le fuseau de l'APPLICATION (Europe/Paris), pas celui de la JVM.
        // `LocalDate.now()` lit le fuseau de la JVM — UTC en conteneur : entre 22 h et minuit
        // UTC, il est déjà le lendemain à Paris, la séance était donc planifiée la veille de ce
        // que `/me/today` considère comme aujourd'hui, et l'écran revenait vide. Un test qui ne
        // tombe qu'entre 22 h et minuit est pire qu'un test absent : il fait douter du code.
        String today = clock.today().toString();
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
        JsonNode athAuth = objectMapper.readTree(mvc.perform(
                        post("/public/invitations/{t}/accept", inviteToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"healthDataConsent\":true,\"termsAccepted\":true,"
                                        + "\"email\":\"portail.athlete@darilab.app\","
                                        + "\"password\":\"athletepass1\"}"))
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

        // « Ma semaine » : le chiffre que l'athlète regarde vraiment (« 0/0 km, 1 séance sur 1 »).
        mvc.perform(get("/me/week-summary").header("Authorization", "Bearer " + athToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedSessions").value(1))
                .andExpect(jsonPath("$.completedSessions").value(1))
                .andExpect(jsonPath("$.weekStart").exists());

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

    /**
     * Confirme l'adresse du coach : depuis le lot 7, inviter un athlète (donc envoyer un e-mail à
     * un tiers) exige une adresse vérifiée. Le jeton de vérification n'est pas exposé par l'API.
     */
    private void verifyCoachEmail(String clubId) {
        com.coachrun.entity.User user = userRepository.findAll().stream()
                .filter(u -> u.getClub() != null && clubId.equals(u.getClub().getId().toString()))
                .filter(u -> u.getRole() == com.coachrun.entity.enums.UserRole.HEAD_COACH)
                .findFirst().orElseThrow();
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
    }
}
