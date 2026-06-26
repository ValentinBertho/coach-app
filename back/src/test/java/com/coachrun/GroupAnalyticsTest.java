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

/** Analytics agrégées d'un groupe : effectif, répartition de forme, volume prévu/réalisé. */
@SpringBootTest
@ActiveProfiles("test")
class GroupAnalyticsTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String token;
    private String clubId;

    @Test
    void groupAnalyticsAggregatesAthletes() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode a = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ga-%s@test.fr","password":"password123","fullName":"Coach GA","clubName":"GA %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        token = a.get("accessToken").asText();
        clubId = a.get("user").get("clubId").asText();

        String groupId = objectMapper.readTree(postJson("/clubs/{c}/groups", "{\"name\":\"Marathon\"}")).get("id").asText();
        createAthleteInGroup(groupId);
        createAthleteInGroup(groupId);

        mvc.perform(get("/clubs/{c}/groups/{g}/analytics", clubId, groupId)
                        .header("Authorization", "Bearer " + token).param("weeks", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.athleteCount").value(2))
                .andExpect(jsonPath("$.athletes.length()").value(2))
                // Sans retour de séance, l'état de forme par défaut est « vert ».
                .andExpect(jsonPath("$.form.green").value(2))
                .andExpect(jsonPath("$.totals.plannedKm").exists());
    }

    private String postJson(String url, String body) throws Exception {
        return mvc.perform(post(url, clubId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
    }

    private void createAthleteInGroup(String groupId) throws Exception {
        mvc.perform(post("/clubs/{c}/athletes", clubId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A" + UUID.randomUUID() + "\",\"lastName\":\"R\",\"groupId\":\"" + groupId + "\"}"))
                .andExpect(status().isCreated());
    }
}
