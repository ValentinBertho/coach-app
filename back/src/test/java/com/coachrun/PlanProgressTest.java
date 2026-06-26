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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suivi de plan : lien séance↔plan, application idempotente, avancement (% réalisé) et
 * suppression propre des séances encore planifiées à la désattribution.
 */
@SpringBootTest
@ActiveProfiles("test")
class PlanProgressTest {

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
                                {"email":"pp-%s@test.fr","password":"password123","fullName":"Coach PP","clubName":"PP %s"}
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

    @Test
    void linkIdempotencyProgressAndCleanUnassign() throws Exception {
        auth();
        String athleteId = objectMapper.readTree(postJson("/clubs/{c}/athletes",
                "{\"firstName\":\"Léa\",\"lastName\":\"Run\"}")).get("id").asText();
        String templateId = objectMapper.readTree(postJson("/clubs/{c}/workout-templates",
                "{\"name\":\"EF\",\"type\":\"ENDURANCE\",\"title\":\"Footing\",\"targetDistanceM\":8000,\"steps\":[]}"))
                .get("id").asText();
        String planId = objectMapper.readTree(postJson("/clubs/{c}/training-plans",
                "{\"name\":\"Reprise\",\"durationWeeks\":2,\"items\":[{\"weekIndex\":0,\"dayOfWeek\":2,\"templateId\":\""
                        + templateId + "\"}]}")).get("id").asText();

        String apply = "{\"athleteId\":\"" + athleteId + "\",\"startDate\":\"2026-09-07\"}";
        mvc.perform(post("/clubs/{c}/training-plans/{p}/apply", clubId, planId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(apply))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));

        // Ré-application : idempotente (les séances planifiées sont purgées puis regénérées).
        mvc.perform(post("/clubs/{c}/training-plans/{p}/apply", clubId, planId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(apply))
                .andExpect(status().isOk());

        // La semaine 1 (mardi 2026-09-08) ne contient qu'UNE séance malgré la double application.
        String week = mvc.perform(get("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-09-07").param("to", "2026-09-13"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String workoutId = objectMapper.readTree(week).get(0).get("id").asText();

        // Avancement initial : 1 séance, 0 réalisée.
        mvc.perform(get("/clubs/{c}/training-plans/{p}/athletes/{a}/progress", clubId, planId, athleteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSessions").value(1))
                .andExpect(jsonPath("$.completedSessions").value(0))
                .andExpect(jsonPath("$.percent").value(0))
                .andExpect(jsonPath("$.durationWeeks").value(2));

        // L'athlète réalise la séance → 100 %.
        mvc.perform(patch("/clubs/{c}/athletes/{a}/workouts/{w}/status", clubId, athleteId, workoutId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/clubs/{c}/training-plans/{p}/athletes/{a}/progress", clubId, planId, athleteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedSessions").value(1))
                .andExpect(jsonPath("$.percent").value(100));

        // Désattribution : l'attribution disparaît (404 sur l'avancement) ; l'historique réalisé reste.
        mvc.perform(delete("/clubs/{c}/training-plans/{p}/athletes/{a}", clubId, planId, athleteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/clubs/{c}/training-plans/{p}/athletes/{a}/progress", clubId, planId, athleteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-09-07").param("to", "2026-09-13"))
                .andExpect(jsonPath("$.length()").value(1)); // la séance réalisée est conservée
    }

    @Test
    void planWithStrengthItemSchedulesAndCounts() throws Exception {
        auth();
        String athleteId = objectMapper.readTree(postJson("/clubs/{c}/athletes",
                "{\"firstName\":\"Tom\",\"lastName\":\"Force\"}")).get("id").asText();
        String sessionId = objectMapper.readTree(postJson("/clubs/{c}/pp/sessions",
                "{\"name\":\"Renfo bas du corps\"}")).get("id").asText();

        // Plan mixte : un item de force.
        String planId = objectMapper.readTree(postJson("/clubs/{c}/training-plans",
                "{\"name\":\"Mixte\",\"durationWeeks\":1,\"items\":[{\"weekIndex\":0,\"dayOfWeek\":3,"
                        + "\"kind\":\"STRENGTH\",\"templateId\":\"" + sessionId + "\"}]}")).get("id").asText();

        mvc.perform(post("/clubs/{c}/training-plans/{p}/apply", clubId, planId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"athleteId\":\"" + athleteId + "\",\"startDate\":\"2026-09-07\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));

        // L'avancement compte la séance de force (1 totale, 0 réalisée).
        mvc.perform(get("/clubs/{c}/training-plans/{p}/athletes/{a}/progress", clubId, planId, athleteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSessions").value(1))
                .andExpect(jsonPath("$.completedSessions").value(0));

        // Le plan expose l'item avec sa nature.
        mvc.perform(get("/clubs/{c}/training-plans/{p}", clubId, planId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].kind").value("STRENGTH"))
                .andExpect(jsonPath("$.items[0].templateName").value("Renfo bas du corps"));
    }

    @Test
    void planWithMesocycleScalesLoadPerWeek() throws Exception {
        auth();
        String athleteId = objectMapper.readTree(postJson("/clubs/{c}/athletes",
                "{\"firstName\":\"Max\",\"lastName\":\"Charge\"}")).get("id").asText();
        String templateId = objectMapper.readTree(postJson("/clubs/{c}/workout-templates",
                "{\"name\":\"SL\",\"type\":\"ENDURANCE\",\"title\":\"Sortie longue\",\"targetDistanceM\":10000,\"steps\":[]}"))
                .get("id").asText();
        String mesoId = objectMapper.readTree(postJson("/clubs/{c}/mesocycle-templates",
                "{\"name\":\"3:1\",\"weeks\":4,\"increasePct\":10,\"deloadEvery\":4,\"deloadPct\":60}")).get("id").asText();

        // Plan de 4 semaines (1 séance/sem) porté par le mésocycle.
        String planId = objectMapper.readTree(postJson("/clubs/{c}/training-plans",
                "{\"name\":\"Bloc\",\"durationWeeks\":4,\"mesocycleTemplateId\":\"" + mesoId + "\",\"items\":["
                        + "{\"weekIndex\":0,\"dayOfWeek\":2,\"templateId\":\"" + templateId + "\"},"
                        + "{\"weekIndex\":1,\"dayOfWeek\":2,\"templateId\":\"" + templateId + "\"},"
                        + "{\"weekIndex\":2,\"dayOfWeek\":2,\"templateId\":\"" + templateId + "\"},"
                        + "{\"weekIndex\":3,\"dayOfWeek\":2,\"templateId\":\"" + templateId + "\"}]}")).get("id").asText();

        mvc.perform(post("/clubs/{c}/training-plans/{p}/apply", clubId, planId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"athleteId\":\"" + athleteId + "\",\"startDate\":\"2026-09-07\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(4));

        // Montée 10k → 11k → 12k puis décharge à 6k (×0.6).
        assertDistance(athleteId, "2026-09-08", 10000);
        assertDistance(athleteId, "2026-09-15", 11000);
        assertDistance(athleteId, "2026-09-22", 12000);
        assertDistance(athleteId, "2026-09-29", 6000);
    }

    private void assertDistance(String athleteId, String date, int expected) throws Exception {
        mvc.perform(get("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .param("from", date).param("to", date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetDistanceM").value(expected));
    }
}
