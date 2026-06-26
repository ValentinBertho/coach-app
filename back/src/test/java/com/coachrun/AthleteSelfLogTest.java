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

/**
 * Saisie côté athlète : un athlète connecté consigne une sortie libre depuis son portail
 * et la retrouve dans ses activités (source MANUAL).
 */
@SpringBootTest
@ActiveProfiles("test")
class AthleteSelfLogTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;

    @Test
    void athleteLogsFreeRunAndSeesIt() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"coach-%s@test.fr","password":"password123","fullName":"C","clubName":"SL %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String coach = "Bearer " + auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();

        String email = "self-" + UUID.randomUUID() + "@darilab.app";
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", coach).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Léa\",\"lastName\":\"Run\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        String url = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/invitation", clubId, athleteId)
                        .header("Authorization", coach))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("inviteUrl").asText();
        String token = url.substring(url.lastIndexOf('/') + 1);

        mvc.perform(post("/public/invitations/{t}/accept", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"healthDataConsent\":true,\"password\":\"athletepass1\"}"))
                .andExpect(status().isOk());

        String athlete = "Bearer " + objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"athletepass1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();

        // L'athlète consigne une sortie libre.
        mvc.perform(post("/me/activities").header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityDate\":\"2026-06-20\",\"title\":\"Sortie libre dimanche\","
                                + "\"distanceM\":10500,\"durationS\":3300}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.distanceM").value(10500));

        // Et la retrouve dans ses activités.
        mvc.perform(get("/me/activities").header("Authorization", athlete))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Sortie libre dimanche"));
    }
}
