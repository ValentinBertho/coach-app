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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class WorkoutControllerTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String[] coachWithAthlete(MockMvc mvc) throws Exception {
        String reg = """
                {"email":"w-%s@test.fr","password":"password123","fullName":"C","termsAccepted": true, "clubName":"WC %s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(reg))
                .andReturn().getResponse().getContentAsString());
        String token = auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();

        String athlete = mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andReturn().getResponse().getContentAsString();
        String athleteId = objectMapper.readTree(athlete).get("id").asText();
        return new String[] { token, clubId, athleteId };
    }

    @Test
    void createWorkoutWithStepsAndCalendarAndStatusTransition() throws Exception {
        MockMvc mvc = mockMvc();
        String[] ctx = coachWithAthlete(mvc);
        String token = ctx[0], clubId = ctx[1], athleteId = ctx[2];

        String body = """
                {
                  "scheduledDate":"2026-07-01","type":"INTERVALS","title":"VMA 10x400",
                  "steps":[
                    {"stepType":"WARMUP","repetitions":1,"zone":"Z2","durationS":900},
                    {"stepType":"REPETITION","repetitions":10,"zone":"Z5","distanceM":400},
                    {"stepType":"COOLDOWN","repetitions":1,"zone":"Z1","durationS":600}
                  ]
                }
                """;
        String created = mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.steps.length()").value(3))
                .andExpect(jsonPath("$.steps[1].repetitions").value(10))
                .andReturn().getResponse().getContentAsString();
        String workoutId = objectMapper.readTree(created).get("id").asText();

        // calendrier (plage incluant la date)
        mvc.perform(get("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-29").param("to", "2026-07-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // replanification (glisser-déposer)
        mvc.perform(patch("/clubs/{c}/athletes/{a}/workouts/{w}/reschedule", clubId, athleteId, workoutId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scheduledDate\":\"2026-07-02\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDate").value("2026-07-02"));

        // PLANNED → COMPLETED
        mvc.perform(patch("/clubs/{c}/athletes/{a}/workouts/{w}/status", clubId, athleteId, workoutId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Et la correction : COMPLETED → PARTIAL.
        //
        // Ce test attendait ici un 409, au nom d'une machine à états qui n'autorisait, depuis un
        // état terminal, que le retour à PLANNED. L'attente était fausse : un statut est une
        // déclaration, et se corriger est le geste le plus banal qui soit — un athlète qui
        // rouvrait son débrief pour passer « écourtée » à « réalisée » se voyait opposer
        // « Transition de statut interdite ». Le même résultat s'obtenait de toute façon en deux
        // temps, en repassant par PLANNED : la garde ne protégeait rien.
        mvc.perform(patch("/clubs/{c}/athletes/{a}/workouts/{w}/status", clubId, athleteId, workoutId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PARTIAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"));
    }

    @Test
    void importPlanIsReimportableAndNeverRewritesHistory() throws Exception {
        MockMvc mvc = mockMvc();
        String[] ctx = coachWithAthlete(mvc);
        String token = ctx[0], clubId = ctx[1], athleteId = ctx[2];

        String plan = """
                {"title":"Plan test","workouts":[
                  {"scheduledDate":"2026-07-14","type":"INTERVALS","title":"Côtes","notes":"10*20''",
                   "steps":[{"stepType":"WARMUP","repetitions":1,"zone":"Z1","durationS":1800}]},
                  {"scheduledDate":"2026-07-15","type":"ENDURANCE","title":"Footing","targetDurationS":3000}
                ]}
                """;
        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts/import", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(plan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2));

        // Le même fichier, une seconde fois : rien ne bouge, rien ne se duplique.
        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts/import", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(plan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.unchanged").value(2));

        // Le footing est fait ; le coach envoie une version corrigée des deux séances.
        String calendar = mvc.perform(get("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-07-15").param("to", "2026-07-15"))
                .andReturn().getResponse().getContentAsString();
        String footingId = objectMapper.readTree(calendar).get(0).get("id").asText();
        mvc.perform(patch("/clubs/{c}/athletes/{a}/workouts/{w}/status", clubId, athleteId, footingId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());

        String revised = plan.replace("10*20''", "12*20''").replace("3000", "3600");
        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts/import", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(revised))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.kept").value(1));

        mvc.perform(get("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-07-13").param("to", "2026-07-19"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].notes").value("12*20''"))
                .andExpect(jsonPath("$[1].targetDurationS").value(3000));
    }
}
