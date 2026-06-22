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
class TemplatePlanMessageTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void templateApplyPlanApplyAndMessaging() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"p1-%s@test.fr","password":"password123","fullName":"Coach P1","clubName":"P1 %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String token = auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();

        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Léa\",\"lastName\":\"Run\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // Bibliothèque : créer un modèle avec étapes
        String tpl = mvc.perform(post("/clubs/{c}/workout-templates", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"VMA courte","type":"INTERVALS","title":"VMA 10x400","targetDistanceM":9000,
                                 "steps":[{"stepType":"WARMUP","repetitions":1,"zone":"Z2","durationS":900},
                                          {"stepType":"REPETITION","repetitions":10,"zone":"Z5","distanceM":400}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String templateId = objectMapper.readTree(tpl).get("id").asText();

        // Appliquer le modèle au calendrier
        mvc.perform(post("/clubs/{c}/workout-templates/{t}/apply", clubId, templateId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"athleteId\":\"" + athleteId + "\",\"date\":\"2026-07-06\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("VMA 10x400"))
                .andExpect(jsonPath("$.steps.length()").value(2));

        // Plan périodisé d'1 semaine avec 1 item
        String plan = mvc.perform(post("/clubs/{c}/training-plans", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bloc VMA","durationWeeks":1,
                                 "items":[{"weekIndex":0,"dayOfWeek":2,"templateId":"%s"}]}""".formatted(templateId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String planId = objectMapper.readTree(plan).get("id").asText();

        mvc.perform(post("/clubs/{c}/training-plans/{p}/apply", clubId, planId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"athleteId\":\"" + athleteId + "\",\"startDate\":\"2026-08-03\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        // Messagerie coach → athlète
        mvc.perform(post("/clubs/{c}/athletes/{a}/messages", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Bravo pour ta séance !\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderRole").value("HEAD_COACH"));

        mvc.perform(get("/clubs/{c}/athletes/{a}/messages", clubId, athleteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
