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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'athlète lit ses propres zones d'entraînement.
 *
 * <p>Il voyait l'allure prescrite d'<b>une</b> séance, jamais l'échelle dont elle sort : la table
 * qui dit à quelle allure et à quelle fréquence cardiaque chacune de ses zones se court. C'est la
 * donnée qu'on relit avant de partir courir, et c'était la seule de sa fiche qu'il ne pouvait pas
 * atteindre.</p>
 *
 * <p>Ce que ces tests protègent : que les deux écrans montrent <b>le même tableau</b>. Une échelle
 * qui diverge entre coach et athlète ne serait pas une gêne d'affichage — ils ne parleraient plus
 * de la même séance.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AthleteZonesVisibilityTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String coachBearer;
    private String athleteBearer;
    private String clubId;
    private String athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode coach = login(DemoSeedService.HEAD_COACH_EMAIL);
        coachBearer = "Bearer " + coach.get("accessToken").asText();
        clubId = coach.get("user").get("clubId").asText();
        JsonNode athlete = login(DemoSeedService.ATHLETE_EMAIL);
        athleteBearer = "Bearer " + athlete.get("accessToken").asText();
        athleteId = athlete.get("user").get("athleteId").asText();
    }

    /** L'écran existe, et il porte des zones nommées — pas une liste vide. */
    @Test
    void theAthleteReadsHisOwnZones() throws Exception {
        JsonNode zones = athleteZones();

        assertThat(zones.isArray()).isTrue();
        assertThat(zones).as("l'athlete doit voir une echelle, pas un ecran vide").isNotEmpty();
        assertThat(zones.get(0).get("name").asText()).isNotBlank();
        assertThat(zones.get(0).get("metrics").isArray()).isTrue();
    }

    /**
     * La même échelle que celle du coach : mêmes zones, dans le même ordre.
     *
     * <p>C'est l'invariant central. Le portail compose sa réponse côté serveur là où le coach
     * assemble trois catalogues dans son navigateur : deux chemins différents, un seul résultat
     * admissible.</p>
     */
    @Test
    void theAthleteScaleMatchesTheCoachScale() throws Exception {
        List<String> coachZones = new ArrayList<>();
        coachZones(); // premier accès côté coach : provoque le même pré-remplissage
        for (JsonNode z : coachZones()) {
            coachZones.add(z.get("id").asText());
        }

        List<String> athleteZones = new ArrayList<>();
        for (JsonNode z : athleteZones()) {
            athleteZones.add(z.get("zoneId").asText());
        }

        // Sans ce garde-fou, deux echelles vides passeraient le test sans rien prouver.
        assertThat(coachZones).as("le club de demonstration doit porter une echelle").isNotEmpty();
        assertThat(athleteZones)
                .as("coach et athlete doivent lire la meme echelle, dans le meme ordre")
                .isEqualTo(coachZones);
    }

    /**
     * Les valeurs sont celles de l'athlète, pas des cases vides : l'écran ne sert à rien s'il
     * n'affiche pas de fourchette.
     */
    @Test
    void zonesCarryComputedValues() throws Exception {
        long withValues = 0;
        for (JsonNode z : athleteZones()) {
            for (JsonNode m : z.get("metrics")) {
                if (!m.get("valueMin").isNull() || !m.get("valueMax").isNull()) {
                    withValues++;
                }
            }
        }
        assertThat(withValues)
                .as("les zones de l'athlete doivent porter des valeurs derivees de son profil")
                .isGreaterThan(0);
    }

    /** Un ajustement du coach se lit chez l'athlète : une seule vérité, pas une copie figée. */
    @Test
    void aCoachAdjustmentIsVisibleToTheAthlete() throws Exception {
        JsonNode target = firstMetricCell();
        String zoneId = target.get("zoneId").asText();
        String metricId = target.get("metricTypeId").asText();

        mvc.perform(put("/clubs/{c}/athletes/{a}/zone-values/{z}/{m}", clubId, athleteId, zoneId, metricId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("valueMin", 301.0, "valueMax", 317.0, "locked", true))))
                .andExpect(status().isOk());

        JsonNode cell = cellOf(athleteZones(), zoneId, metricId);
        assertThat(cell).as("la cellule ajustee doit exister cote athlete").isNotNull();
        assertThat(cell.get("valueMin").asDouble()).isEqualTo(301.0);
        assertThat(cell.get("valueMax").asDouble()).isEqualTo(317.0);
        assertThat(cell.get("source").asText())
                .as("l'athlete doit pouvoir distinguer une valeur fixee par son coach")
                .isEqualTo("MANUAL");
    }

    /**
     * La règle accompagne la fourchette. Sans elle, une allure est un chiffre tombé du ciel ;
     * avec elle, l'athlète sait à quoi la rattacher — et pourquoi elle bougera à son prochain test.
     */
    @Test
    void zonesExposeTheRuleBehindTheNumbers() throws Exception {
        boolean anyRule = false;
        for (JsonNode z : athleteZones()) {
            for (JsonNode m : z.get("metrics")) {
                if (!m.get("anchor").isNull() && !m.get("lowPct").isNull()) {
                    anyRule = true;
                }
            }
        }
        assertThat(anyRule).as("au moins une zone doit exposer son ancre et sa fourchette de %").isTrue();
    }

    /** Le portail reste le portail : il ne répond que pour l'athlète connecté. */
    @Test
    void theEndpointIsScopedToTheSignedInAthlete() throws Exception {
        mvc.perform(get("/me/zones").header("Authorization", coachBearer))
                .andExpect(status().is4xxClientError());
    }

    // --- Utilitaires ---------------------------------------------------------------------------

    private JsonNode athleteZones() throws Exception {
        return objectMapper.readTree(mvc.perform(get("/me/zones")
                        .header("Authorization", athleteBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode coachZones() throws Exception {
        return objectMapper.readTree(mvc.perform(get("/clubs/{c}/training-zones", clubId)
                        .header("Authorization", coachBearer)
                        .param("athleteId", athleteId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** Première cellule zone × métrique de l'échelle : n'importe laquelle fait l'affaire. */
    private JsonNode firstMetricCell() throws Exception {
        for (JsonNode z : athleteZones()) {
            for (JsonNode m : z.get("metrics")) {
                return objectMapper.createObjectNode()
                        .put("zoneId", z.get("zoneId").asText())
                        .put("metricTypeId", m.get("metricTypeId").asText());
            }
        }
        throw new AssertionError("aucune metrique de zone dans le jeu de demonstration");
    }

    private JsonNode cellOf(JsonNode zones, String zoneId, String metricId) {
        for (JsonNode z : zones) {
            if (!zoneId.equals(z.get("zoneId").asText())) {
                continue;
            }
            for (JsonNode m : z.get("metrics")) {
                if (metricId.equals(m.get("metricTypeId").asText())) {
                    return m;
                }
            }
        }
        return null;
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
