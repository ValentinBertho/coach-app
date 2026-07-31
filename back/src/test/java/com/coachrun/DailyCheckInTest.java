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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Check-in matinal : trois curseurs qui alimentent l'état de forme <strong>avant</strong> la
 * séance, et non plus seulement après.
 *
 * <p>Vérifie l'invariant central au passage : la forme se calcule sur fatigue + douleur. Le
 * sommeil est stocké et rendu au coach, mais il ne pèse jamais sur la pastille — pas plus que
 * le RPE.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DailyCheckInTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String athleteBearer;
    private String coachBearer;
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

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode saveCheckIn(String body) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/me/checkin")
                        .header("Authorization", athleteBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** Ligne de l'athlète dans le tableau de bord « forme » du coach. */
    private JsonNode formRow() throws Exception {
        JsonNode dash = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/dashboard/form", clubId).header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (String list : new String[] { "routeAthletes", "trailAthletes" }) {
            for (JsonNode row : dash.get(list)) {
                if (athleteId.equals(row.get("id").asText())) {
                    return row;
                }
            }
        }
        throw new AssertionError("Athlète absent du tableau de bord de forme");
    }

    @Test
    void checkInIsEmptyThenSavedThenCorrected() throws Exception {
        mvc.perform(get("/me/checkin").header("Authorization", athleteBearer))
                .andExpect(status().isNoContent());

        JsonNode saved = saveCheckIn("{\"sleep\":7,\"fatigue\":4,\"pain\":1}");
        assertThat(saved.get("sleep").asInt()).isEqualTo(7);
        assertThat(saved.get("fatigue").asInt()).isEqualTo(4);
        assertThat(saved.get("pain").asInt()).isEqualTo(1);

        // Rouvrir le check-in du matin le CORRIGE : une ligne par athlète et par jour.
        JsonNode corrected = saveCheckIn("{\"sleep\":3,\"fatigue\":9,\"pain\":2}");
        assertThat(corrected.get("fatigue").asInt()).isEqualTo(9);

        JsonNode reread = objectMapper.readTree(mvc.perform(get("/me/checkin")
                        .header("Authorization", athleteBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(reread.get("fatigue").asInt()).isEqualTo(9);
        assertThat(reread.get("sleep").asInt()).isEqualTo(3);
    }

    @Test
    void checkInFeedsCoachFormBeforeAnySession() throws Exception {
        // Fatigue 9 + douleur 6 déclarées au réveil, aucune séance notée aujourd'hui.
        saveCheckIn("{\"sleep\":2,\"fatigue\":9,\"pain\":6}");

        JsonNode row = formRow();
        assertThat(row.get("fatigue").asInt()).isEqualTo(9);
        assertThat(row.get("pain").asInt()).isEqualTo(6);
        assertThat(row.get("formStatus").asText()).isEqualTo("RED");
        // La pastille n'est plus « périmée » : le coach voit la forme du jour, dès le matin.
        assertThat(row.get("lastFeedbackDate").asText()).isEqualTo(java.time.LocalDate.now().toString());
    }

    @Test
    void sleepAloneNeverDrivesTheFormStatus() throws Exception {
        // Sommeil catastrophique, mais fatigue et douleur au plus bas : la forme reste verte.
        // Le sommeil est du contexte pour le coach, jamais un terme du calcul — comme le RPE.
        saveCheckIn("{\"sleep\":0,\"fatigue\":0,\"pain\":0}");

        assertThat(formRow().get("formStatus").asText()).isEqualTo("GREEN");
    }

    @Test
    void checkInIsScopedToItsOwnAthlete() throws Exception {
        saveCheckIn("{\"sleep\":5,\"fatigue\":5,\"pain\":0}");

        // Le portail /me est scopé par le token : un coach n'y a simplement pas accès.
        mvc.perform(get("/me/checkin").header("Authorization", coachBearer))
                .andExpect(status().isForbidden());
    }
}
