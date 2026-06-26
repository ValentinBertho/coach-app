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
 * Planification de groupe : modèles de mésocycle réutilisables, génération d'un mésocycle pour
 * tout un groupe à partir de la semaine source de chaque athlète, et application d'un plan à un groupe.
 */
@SpringBootTest
@ActiveProfiles("test")
class GroupPlanningTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String token;
    private String clubId;

    private void auth() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode a = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"gp-%s@test.fr","password":"password123","fullName":"Coach GP","clubName":"GP %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        token = a.get("accessToken").asText();
        clubId = a.get("user").get("clubId").asText();
    }

    private String postJson(String url, String body) throws Exception {
        return mvc.perform(post(url, clubId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
    }

    private String createAthleteInGroup(String groupId) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A" + UUID.randomUUID() + "\",\"lastName\":\"R\",\"groupId\":\"" + groupId + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    /** Crée une séance dans la semaine source (lundi 2026-07-06) pour servir de base au mésocycle. */
    private void seedSourceWeek(String athleteId) throws Exception {
        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"2026-07-08\",\"type\":\"ENDURANCE\",\"title\":\"Sortie longue\","
                                + "\"targetDistanceM\":12000,\"steps\":[]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void mesocycleTemplateCrudAndApplyToGroup() throws Exception {
        auth();
        String groupId = objectMapper.readTree(postJson("/clubs/{c}/groups", "{\"name\":\"Marathon\"}"))
                .get("id").asText();
        String a1 = createAthleteInGroup(groupId);
        String a2 = createAthleteInGroup(groupId);
        seedSourceWeek(a1);
        seedSourceWeek(a2);

        // Création d'un « méso type » réutilisable (bloc 4 semaines 3:1).
        String tpl = postJson("/clubs/{c}/mesocycle-templates",
                "{\"name\":\"Bloc 4 sem 3:1\",\"weeks\":4,\"increasePct\":10,\"deloadEvery\":4,\"deloadPct\":60}");
        String mesoId = objectMapper.readTree(tpl).get("id").asText();

        mvc.perform(get("/clubs/{c}/mesocycle-templates", clubId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].weeks").value(4));

        // Génération du mésocycle pour tout le groupe à partir du modèle.
        mvc.perform(post("/clubs/{c}/groups/{g}/generate-mesocycle", clubId, groupId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mesocycleTemplateId\":\"" + mesoId + "\",\"sourceWeekStart\":\"2026-07-06\","
                                + "\"firstWeekStart\":\"2026-07-13\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.athletes").value(2))
                .andExpect(jsonPath("$.skipped").value(0))
                // 2 athlètes × 4 semaines × 1 séance = 8.
                .andExpect(jsonPath("$.created").value(8));
    }

    @Test
    void applyTrainingPlanToGroup() throws Exception {
        auth();
        String groupId = objectMapper.readTree(postJson("/clubs/{c}/groups", "{\"name\":\"Débutants\"}"))
                .get("id").asText();
        String a1 = createAthleteInGroup(groupId);
        String a2 = createAthleteInGroup(groupId);

        String templateId = objectMapper.readTree(postJson("/clubs/{c}/workout-templates",
                "{\"name\":\"EF\",\"type\":\"ENDURANCE\",\"title\":\"Footing\",\"targetDistanceM\":8000,\"steps\":[]}"))
                .get("id").asText();

        String planId = objectMapper.readTree(postJson("/clubs/{c}/training-plans",
                "{\"name\":\"Reprise\",\"durationWeeks\":1,\"items\":[{\"weekIndex\":0,\"dayOfWeek\":3,\"templateId\":\""
                        + templateId + "\"}]}")).get("id").asText();

        mvc.perform(post("/clubs/{c}/training-plans/{p}/apply-group", clubId, planId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":\"" + groupId + "\",\"startDate\":\"2026-09-07\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.athletes").value(2))
                .andExpect(jsonPath("$.created").value(2));
    }
}
