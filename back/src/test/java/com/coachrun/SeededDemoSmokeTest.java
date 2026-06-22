package com.coachrun;

import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reproduit le parcours d'une démo : seed → login coach démo → chargement de tous les
 * écrans principaux. Doit être 200 partout (détecte les 500 de chargement).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SeededDemoSmokeTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DemoSeedService demoSeedService;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void demoCoachCanLoadAllScreens() throws Exception {
        demoSeedService.seed();
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoSeedService.HEAD_COACH_EMAIL
                                + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String token = auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();
        String bearer = "Bearer " + token;

        mvc.perform(get("/clubs/{c}/dashboard", clubId).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/clubs/{c}/athletes", clubId).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/clubs/{c}/athletes?q=a&page=0", clubId).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/clubs/{c}/groups", clubId).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/clubs/{c}/workout-templates", clubId).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/clubs/{c}/training-plans", clubId).header("Authorization", bearer)).andExpect(status().isOk());

        // un athlète du club → ses sous-ressources
        String athletes = mvc.perform(get("/clubs/{c}/athletes", clubId).header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString();
        String athleteId = objectMapper.readTree(athletes).get("content").get(0).get("id").asText();

        mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, athleteId).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/clubs/{c}/athletes/{a}/activities", clubId, athleteId).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/clubs/{c}/athletes/{a}/analytics", clubId, athleteId).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/clubs/{c}/athletes/{a}/races", clubId, athleteId).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/clubs/{c}/athletes/{a}/messages", clubId, athleteId).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/clubs/{c}/athletes/{a}/workouts?from=2026-01-01&to=2026-12-31", clubId, athleteId)
                .header("Authorization", bearer)).andExpect(status().isOk());
    }
}
