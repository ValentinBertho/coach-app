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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Indisponibilités athlète : CRUD coach + lecture portail + garde-fou de dates. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UnavailabilityControllerTest {

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

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void coachCreatesAndAthleteSeesUnavailability() throws Exception {
        String from = java.time.LocalDate.now().toString();
        String to = java.time.LocalDate.now().plusDays(7).toString();
        String id = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/unavailabilities", clubId, athleteId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + from + "\",\"endDate\":\"" + to + "\",\"reason\":\"ILLNESS\",\"notes\":\"Grippe\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // L'athlète voit son indisponibilité en cours.
        JsonNode mine = objectMapper.readTree(mvc.perform(get("/me/unavailabilities")
                        .header("Authorization", athleteBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(mine).anyMatch(u -> u.get("reason").asText().equals("ILLNESS"));

        // Suppression par le coach.
        mvc.perform(delete("/clubs/{c}/athletes/{a}/unavailabilities/{id}", clubId, athleteId, id)
                        .header("Authorization", coachBearer))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsEndBeforeStart() throws Exception {
        String from = java.time.LocalDate.now().toString();
        String to = java.time.LocalDate.now().minusDays(2).toString();
        mvc.perform(post("/clubs/{c}/athletes/{a}/unavailabilities", clubId, athleteId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + from + "\",\"endDate\":\"" + to + "\",\"reason\":\"OTHER\"}"))
                .andExpect(status().isBadRequest());
    }
}
