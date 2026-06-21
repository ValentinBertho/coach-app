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

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private record Coach(String token, String clubId) {
    }

    private Coach registerCoach(MockMvc mvc, String email) throws Exception {
        String body = """
                {"email":"%s","password":"password123","fullName":"C","clubName":"Club %s"}
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

        // invitation
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
