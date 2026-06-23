package com.coachrun;

import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bibliothèque de séances course DARI Lab : arbre de catégories + structure de blocs prescrits
 * en fourchettes + calcul de toute la séance pour un athlète.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseSessionLibraryTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String bearer;
    private String clubId;
    private String athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoSeedService.HEAD_COACH_EMAIL
                                + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        bearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();

        JsonNode athletes = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes?size=50", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        athleteId = athletes.get("content").get(0).get("id").asText();
    }

    @Test
    void categoryTreeCrud() throws Exception {
        // Racine
        JsonNode root = objectMapper.readTree(mvc.perform(post("/clubs/{c}/session-categories", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vitesse\",\"discipline\":\"ROUTE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String rootId = root.get("id").asText();

        // Enfant rattaché à la racine
        JsonNode child = objectMapper.readTree(mvc.perform(post("/clubs/{c}/session-categories", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VMA\",\"parentId\":\"" + rootId + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(child.get("parentId").asText()).isEqualTo(rootId);

        JsonNode list = objectMapper.readTree(mvc.perform(get("/clubs/{c}/session-categories", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(list).hasSize(2);

        // Une catégorie ne peut pas être son propre parent.
        mvc.perform(put("/clubs/{c}/session-categories/{id}", clubId, rootId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vitesse\",\"parentId\":\"" + rootId + "\"}"))
                .andExpect(status().isConflict());

        // Suppression de la racine : l'enfant est détaché (FK SET NULL), pas d'erreur.
        mvc.perform(delete("/clubs/{c}/session-categories/{id}", clubId, rootId)
                .header("Authorization", bearer)).andExpect(status().isNoContent());
    }

    @Test
    void structureStoredAndSessionCalculatedForAthlete() throws Exception {
        // Profil + perf pour disposer d'allures VDOT.
        mvc.perform(put("/clubs/{c}/athletes/{a}/physio", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lt1Ms\":3.5,\"lt2Ms\":3.9,\"vcMs\":4.2,\"fcLt1\":148,\"fcLt2\":163,\"fcMax\":178}"))
                .andExpect(status().isOk());
        mvc.perform(post("/clubs/{c}/athletes/{a}/performances", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distance\":\"D5KM\",\"timeSeconds\":1197}"))
                .andExpect(status().isCreated());

        // Modèle de séance (CRUD existant).
        JsonNode tpl = objectMapper.readTree(mvc.perform(post("/clubs/{c}/workout-templates", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VMA 6x1000\",\"type\":\"INTERVALS\",\"title\":\"VMA\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String templateId = tpl.get("id").asText();

        // Structure DARI Lab : corps = 6 × 1000 m à 98–103 % allure 5 km, récup trot 90 s.
        String structure = """
            {"discipline":"ROUTE","favorite":true,"structure":{
              "warmup":[{"type":"easy","durationS":900,"prescription":{"ref":"PCT_LT1","minPct":75,"maxPct":88}}],
              "main":[{"type":"intervals","reps":6,"distanceM":1000,
                       "prescription":{"ref":"PCT_PACE_5KM","minPct":98,"maxPct":103},
                       "recovery":{"type":"jog","durationS":90,
                                   "prescription":{"ref":"PCT_LT1","minPct":60,"maxPct":75}}}],
              "cooldown":[{"type":"easy","durationS":600,"prescription":{"ref":"PCT_LT1","minPct":60,"maxPct":80}}]
            }}""";
        JsonNode put = objectMapper.readTree(mvc.perform(
                        put("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                                .content(structure))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(put.get("favorite").asBoolean()).isTrue();
        assertThat(put.get("structure").get("main")).hasSize(1);

        // Calcul de toute la séance pour l'athlète.
        JsonNode calc = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workout-templates/{t}/calculated", clubId, athleteId, templateId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode mainEntry = calc.get("main").get(0);
        assertThat(mainEntry.get("calc").get("computable").asBoolean()).isTrue();
        assertThat(mainEntry.get("calc").get("estimatedDistanceM").asInt()).isEqualTo(6000);
        assertThat(mainEntry.get("recoveryCalc").get("computable").asBoolean()).isTrue();
        // Totaux : au moins les 6 km du corps.
        assertThat(calc.get("totalDistanceM").asInt()).isGreaterThanOrEqualTo(6000);
        assertThat(calc.get("totalDurationS").asInt()).isGreaterThan(0);
    }
}
