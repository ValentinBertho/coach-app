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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AthleteControllerTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private com.coachrun.repository.UserRepository userRepository;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private record Coach(String token, String clubId) {
    }

    /** Confirme l'adresse du coach (le jeton de vérification n'est pas exposé par l'API). */
    private void verifyEmailOf(Coach coach) {
        com.coachrun.entity.User user = userRepository.findAll().stream()
                .filter(u -> coach.clubId().equals(
                        u.getClub() != null ? u.getClub().getId().toString() : null))
                .findFirst().orElseThrow();
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
    }

    private Coach registerCoach(MockMvc mvc, String email) throws Exception {
        String body = """
                {"email":"%s","password":"password123","fullName":"C","termsAccepted": true, "clubName":"Club %s"}
                """.formatted(email, UUID.randomUUID());
        String res = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(res);
        return new Coach(json.get("accessToken").asText(), json.get("user").get("clubId").asText());
    }

    @Test
    void crudAndEncryptionRoundTrip() throws Exception {
        MockMvc mvc = mockMvc();
        Coach coach = registerCoach(mvc, "a-" + UUID.randomUUID() + "@test.fr");

        String athleteBody = """
                {"firstName":"Marie","lastName":"Durand","hrMax":190,"hrRest":48,"vma":16.5,"weightKg":58.2}
                """;

        // création + données de santé restituées (déchiffrées)
        String created = mvc.perform(post("/clubs/{c}/athletes", coach.clubId())
                        .header("Authorization", "Bearer " + coach.token())
                        .contentType(MediaType.APPLICATION_JSON).content(athleteBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Marie"))
                .andExpect(jsonPath("$.hrMax").value(190))
                .andExpect(jsonPath("$.vma").value(16.5))
                .andReturn().getResponse().getContentAsString();
        String athleteId = objectMapper.readTree(created).get("id").asText();

        // liste paginée
        mvc.perform(get("/clubs/{c}/athletes", coach.clubId())
                        .header("Authorization", "Bearer " + coach.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Durand"));

        // Inviter, c'est envoyer un e-mail à un tiers : tant que l'adresse du coach n'est pas
        // vérifiée, c'est refusé (le reste de l'application lui reste ouvert).
        mvc.perform(post("/clubs/{c}/athletes/{a}/invitation", coach.clubId(), athleteId)
                        .header("Authorization", "Bearer " + coach.token()))
                .andExpect(status().isForbidden());

        verifyEmailOf(coach);

        mvc.perform(post("/clubs/{c}/athletes/{a}/invitation", coach.clubId(), athleteId)
                        .header("Authorization", "Bearer " + coach.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteUrl").exists());
    }

    @Test
    void crossTenantAccessIsForbidden() throws Exception {
        MockMvc mvc = mockMvc();
        Coach a = registerCoach(mvc, "owner-" + UUID.randomUUID() + "@test.fr");
        Coach b = registerCoach(mvc, "intruder-" + UUID.randomUUID() + "@test.fr");

        // le coach B tente de lister les athlètes du club de A → 403
        mvc.perform(get("/clubs/{c}/athletes", a.clubId())
                        .header("Authorization", "Bearer " + b.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc().perform(get("/clubs/{c}/athletes", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
