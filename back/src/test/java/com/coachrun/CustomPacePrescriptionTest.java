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
 * Allure d'intervalle choisie par le coach : « 6 × 1000 à 130–135 % de LT2 ».
 *
 * <p>Les zones du club sont une échelle de référence, pas un réglage de séance : elles ne se
 * retouchent pas pour un fractionnement particulier. Le couple {@code ref + %} existait déjà côté
 * moteur mais restait réservé à la lecture de l'historique — et la migration douce Z4 recollait
 * une zone sur tout bloc en % à la relecture. Un pourcentage saisi par le coach était donc
 * remplacé, dès l'aller-retour suivant dans l'éditeur, par la bande standard la plus proche.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomPacePrescriptionTest {

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

        // LT2 à 3,9 m/s → 256 s/km de base pour les assertions d'allure.
        mvc.perform(put("/clubs/{c}/athletes/{a}/physio", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lt1Ms\":3.5,\"lt2Ms\":3.9,\"vcMs\":4.2,\"fcLt1\":148,\"fcLt2\":163,\"fcMax\":185}"))
                .andExpect(status().isOk());
    }

    private String createTemplate(String name) throws Exception {
        JsonNode tpl = objectMapper.readTree(mvc.perform(post("/clubs/{c}/workout-templates", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"INTERVALS\",\"title\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return tpl.get("id").asText();
    }

    private void putStructure(String templateId, String body) throws Exception {
        mvc.perform(put("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private JsonNode readStructure(String templateId) throws Exception {
        return objectMapper.readTree(mvc.perform(get("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode calculatedMain(String templateId) throws Exception {
        JsonNode calc = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workout-templates/{t}/calculated", clubId, athleteId, templateId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return calc.get("main").get(0).get("calc");
    }

    private static final String CUSTOM_MAIN = """
        {"structure":{"warmup":[],"cooldown":[],
          "main":[{"id":"m1","type":"intervals","reps":6,"distanceM":1000,
                   "prescription":{"ref":"PCT_LT2","minPct":130,"maxPct":135,"custom":true}}]}}""";

    /** La fourchette du coach traverse l'aller-retour éditeur sans se faire recoller une zone. */
    @Test
    void aCoachChosenRangeSurvivesTheEditorRoundTrip() throws Exception {
        String templateId = createTemplate("VMA sur mesure");
        putStructure(templateId, CUSTOM_MAIN);

        JsonNode prescription = readStructure(templateId).get("structure").get("main").get(0).get("prescription");
        assertThat(prescription.get("zoneId").isNull()).isTrue();
        assertThat(prescription.get("ref").asText()).isEqualTo("PCT_LT2");
        assertThat(prescription.get("minPct").asDouble()).isEqualTo(130.0);
        assertThat(prescription.get("maxPct").asDouble()).isEqualTo(135.0);
        assertThat(prescription.get("custom").asBoolean()).isTrue();
    }

    /** Les cibles suivent le pourcentage prescrit, pas la bande standard la plus proche. */
    @Test
    void targetsFollowThePrescribedPercentage() throws Exception {
        String templateId = createTemplate("VMA 6×1000");
        putStructure(templateId, CUSTOM_MAIN);

        JsonNode calc = calculatedMain(templateId);
        assertThat(calc.get("computable").asBoolean()).isTrue();
        assertThat(calc.get("ref").asText()).isEqualTo("PCT_LT2");
        // Base LT2 = 1000 / 3,9 ≈ 256 s/km ; 135 % ⇒ 190 s/km, 130 % ⇒ 197 s/km.
        assertThat(calc.get("paceMinSecPerKm").asInt()).isEqualTo(190);
        assertThat(calc.get("paceMaxSecPerKm").asInt()).isEqualTo(197);
    }

    /**
     * Le scénario qui échouait : l'éditeur relit la structure puis l'auto-sauvegarde telle quelle.
     * Sans le drapeau, la zone déduite à la lecture se persistait et prenait la main sur le %.
     */
    @Test
    void anAutosaveAfterReloadDoesNotSubstituteAZone() throws Exception {
        String templateId = createTemplate("VMA relue");
        putStructure(templateId, CUSTOM_MAIN);

        JsonNode reread = readStructure(templateId).get("structure");
        putStructure(templateId, objectMapper.writeValueAsString(
                objectMapper.createObjectNode().set("structure", reread)));

        JsonNode calc = calculatedMain(templateId);
        assertThat(calc.get("paceMinSecPerKm").asInt()).isEqualTo(190);
        assertThat(calc.get("paceMaxSecPerKm").asInt()).isEqualTo(197);
    }

    /** Non-régression : sans le drapeau, un bloc legacy reçoit toujours sa zone à la lecture. */
    @Test
    void aLegacyRangeIsStillMappedToAZone() throws Exception {
        String templateId = createTemplate("VMA legacy");
        putStructure(templateId, """
            {"structure":{"warmup":[],"cooldown":[],
              "main":[{"id":"m1","type":"intervals","reps":6,"distanceM":1000,
                       "prescription":{"ref":"PCT_LT2","minPct":130,"maxPct":135}}]}}""");

        JsonNode prescription = readStructure(templateId).get("structure").get("main").get(0).get("prescription");
        assertThat(prescription.get("zoneId").isNull()).isFalse();
    }
}
