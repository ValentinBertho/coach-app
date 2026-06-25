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

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Planification en cycles : la duplication d'une semaine recopie les séances vers la semaine
 * cible en conservant le décalage de jour et le contenu.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WeekDuplicationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String bearer;
    private String clubId;

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
    }

    private void createWorkout(String athleteId, LocalDate date, String title) throws Exception {
        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"" + date + "\",\"type\":\"ENDURANCE\",\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void duplicateWeekCopiesSessionsKeepingDayOffset() throws Exception {
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Cycle\",\"lastName\":\"Test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        LocalDate source = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate target = source.plusWeeks(1);
        createWorkout(athleteId, source, "Footing lundi");           // lundi
        createWorkout(athleteId, source.plusDays(2), "Seuil mercredi"); // mercredi

        JsonNode res = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts/duplicate-week", clubId, athleteId)
                                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sourceWeekStart\":\"" + source + "\",\"targetWeekStart\":\"" + target + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(res.get("created").asInt()).isEqualTo(2);

        JsonNode week = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .param("from", target.toString()).param("to", target.plusDays(6).toString())
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(week.size()).isEqualTo(2);
        boolean mondayCopy = false;
        boolean wednesdayCopy = false;
        for (JsonNode w : week) {
            String d = w.get("scheduledDate").asText();
            if (d.equals(target.toString()) && "Footing lundi".equals(w.get("title").asText())) mondayCopy = true;
            if (d.equals(target.plusDays(2).toString()) && "Seuil mercredi".equals(w.get("title").asText())) wednesdayCopy = true;
            assertThat(w.get("status").asText()).isEqualTo("PLANNED");
        }
        assertThat(mondayCopy).isTrue();
        assertThat(wednesdayCopy).isTrue();
    }
}
