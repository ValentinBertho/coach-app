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
 * Chantier Z4 — migration douce des modèles legacy (ref + % → zone) à la lecture, en conservant
 * les champs legacy (réversible), et non-régression du calcul legacy pour l'historique.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TemplateZoneMigrationTest {

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
        JsonNode athletes = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes?size=50", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        athleteId = athletes.get("content").get(0).get("id").asText();
    }

    @Test
    void legacyTemplateGetsMappedZoneOnReadKeepingLegacyFields() throws Exception {
        JsonNode tpl = objectMapper.readTree(mvc.perform(post("/clubs/{c}/workout-templates", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VMA legacy\",\"type\":\"INTERVALS\",\"title\":\"VMA\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String templateId = tpl.get("id").asText();

        // Structure LEGACY (ref + %), sans zoneId.
        String structure = """
            {"structure":{
              "warmup":[{"id":"w1","type":"warmup","durationS":900,"prescription":{"ref":"PCT_LT1","minPct":60,"maxPct":72}}],
              "main":[{"id":"m1","type":"intervals","reps":6,"distanceM":1000,"prescription":{"ref":"PCT_PACE_5KM","minPct":98,"maxPct":103}}],
              "cooldown":[]
            }}""";
        mvc.perform(put("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(structure))
                .andExpect(status().isOk());

        // Lecture : le zoneId déduit est présent, les champs legacy conservés.
        JsonNode read = objectMapper.readTree(mvc.perform(get("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode warmupPrescription = read.get("structure").get("warmup").get(0).get("prescription");
        JsonNode mainPrescription = read.get("structure").get("main").get(0).get("prescription");

        assertThat(warmupPrescription.get("zoneId").isNull()).isFalse();
        assertThat(warmupPrescription.get("ref").asText()).isEqualTo("PCT_LT1"); // legacy conservé
        assertThat(mainPrescription.get("zoneId").isNull()).isFalse();
        assertThat(mainPrescription.get("ref").asText()).isEqualTo("PCT_PACE_5KM");
    }

    @Test
    void legacySnapshotStillComputesForAthlete() throws Exception {
        // Profil + perf pour disposer d'allures.
        mvc.perform(put("/clubs/{c}/athletes/{a}/physio", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lt1Ms\":3.5,\"lt2Ms\":3.9,\"vcMs\":4.2,\"fcLt1\":148,\"fcLt2\":163,\"fcMax\":185}"))
                .andExpect(status().isOk());
        mvc.perform(post("/clubs/{c}/athletes/{a}/performances", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distance\":\"D5KM\",\"timeSeconds\":1197}"))
                .andExpect(status().isCreated());

        JsonNode tpl = objectMapper.readTree(mvc.perform(post("/clubs/{c}/workout-templates", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Seuil legacy\",\"type\":\"THRESHOLD\",\"title\":\"Seuil\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String templateId = tpl.get("id").asText();
        String structure = """
            {"structure":{"warmup":[],"cooldown":[],
              "main":[{"id":"m1","type":"threshold","durationS":1200,"prescription":{"ref":"PCT_LT2","minPct":96,"maxPct":100}}]}}""";
        mvc.perform(put("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(structure))
                .andExpect(status().isOk());

        // Le calcul legacy (ref + %) reste opérationnel — non-régression.
        JsonNode calc = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workout-templates/{t}/calculated", clubId, athleteId, templateId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode entry = calc.get("main").get(0);
        assertThat(entry.get("calc").get("computable").asBoolean()).isTrue();
        assertThat(entry.get("calc").get("ref").asText()).isEqualTo("PCT_LT2");
    }
}
