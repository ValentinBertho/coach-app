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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Préparation physique bout-en-bout : exercice → 1RM athlète → séance de force (format Classique,
 * prescription %RM) → charges calculées en kg.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StrengthSessionControllerTest {

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
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        bearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();

        athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Force\",\"lastName\":\"Test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    @Test
    void buildsSessionAndCalculatesChargesFrom1rm() throws Exception {
        // Exercice
        String exerciseId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/pp/exercises", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Squat\",\"category\":\"FORCE_MAX\",\"muscleGroups\":[\"QUADRICEPS\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // 1RM athlète = 120 kg
        mvc.perform(put("/clubs/{c}/athletes/{a}/pp/1rm", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"" + exerciseId + "\",\"rmKg\":120}"))
                .andExpect(status().isOk());

        // Séance de force
        String sessionId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/pp/sessions", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Force max bas du corps\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // Structure : bloc principal classique, Squat 4×5 à 80–85 % RM, RIR 1–3.
        String structure = """
            {"structure":{"blocks":[
              {"id":"b1","blockType":"PRINCIPAL","format":"CLASSIQUE","exercises":[
                {"exerciseId":"%s","exerciseName":"Squat","setType":"STANDARD",
                 "prescription":{"chargeRefType":"PCT_RM_RANGE","chargePctRmMin":80,"chargePctRmMax":85,
                                 "effortRefType":"RIR_RANGE","rirMin":1,"rirMax":3,
                                 "sets":4,"repsFixed":5,"tempo":"3-1-X-1","restSecMin":120,"restSecMax":180}}]}]}}
            """.formatted(exerciseId);
        JsonNode put = objectMapper.readTree(mvc.perform(
                        put("/clubs/{c}/pp/sessions/{s}/structure", clubId, sessionId)
                                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                                .content(structure))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(put.get("structure").get("blocks")).hasSize(1);

        // Calcul des charges pour l'athlète : 80–85 % de 120 = 96–102 → arrondi 95–102.5.
        JsonNode calc = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/pp/sessions/{s}/calculated", clubId, athleteId, sessionId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode charge = calc.get("blocks").get(0).get("exercises").get(0).get("charge");
        assertThat(charge.get("computable").asBoolean()).isTrue();
        assertThat(charge.get("oneRmKg").asDouble()).isEqualTo(120.0);
        assertThat(charge.get("kgMin").asDouble()).isEqualTo(95.0);
        assertThat(charge.get("kgMax").asDouble()).isEqualTo(102.5);
        // Le libellé exact (avec tiret demi-cadratin) est vérifié dans StrengthChargeEngineTest ;
        // ici on évite l'artefact d'encodage de MockMvc.getContentAsString().
        assertThat(charge.get("effortLabel").asText()).startsWith("RIR 1");
    }
}
