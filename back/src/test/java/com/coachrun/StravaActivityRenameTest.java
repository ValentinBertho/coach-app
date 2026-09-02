package com.coachrun;

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
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * « Morning Run », douze fois dans le mois.
 *
 * <p>Strava nomme lui-même toute sortie que son auteur n'a pas nommée. Le calendrier d'un coach
 * qui suit dix athlètes se remplit alors de lignes strictement indiscernables, et le nom de la
 * séance prescrite en face — le seul qui dise quelque chose — n'apparaît nulle part.</p>
 *
 * <p>Ces tests fixent les deux moitiés de la règle : ce qui est remplacé, et surtout ce qui ne
 * l'est jamais. Le renommage écrase le titre sans rien garder de l'original ; il ne doit donc
 * toucher que ce que personne n'a écrit.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class StravaActivityRenameTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String token;
    private String clubId;
    private String athleteId;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"rename-%s@test.fr","password":"password123","fullName":"C","termsAccepted":true,"clubName":"AC rename %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        token = auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();
        athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    /**
     * Le cas qui justifie la fonctionnalité : la sortie tombe en face d'une séance prescrite, et
     * cette séance porte déjà le nom exact de ce qui a été couru. C'est ce nom-là qu'on veut lire.
     */
    @Test
    void anAutoNamedRunAdoptsTheTitleOfTheWorkoutItMatches() throws Exception {
        planWorkout("2026-07-08", "VMA 10x400");

        importStrava("s-1", "2026-07-08", "Morning Run", 10200, 2700)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.title").value("VMA 10x400"));
    }

    /**
     * Sans séance en face, on n'a que les chiffres de la sortie. Un titre descriptif ne prétend
     * pas savoir ce qu'elle était, mais il la rend reconnaissable dans une liste — ce que
     * « Morning Run » ne fait pas.
     */
    @Test
    void anUnmatchedAutoNamedRunGetsADescriptiveTitle() throws Exception {
        importStrava("s-2", "2026-07-09", "Morning Run", 10200, 2700)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNMATCHED"))
                .andExpect(jsonPath("$.title").value("Course à pied — 10,2 km"));
    }

    /** Strava nomme dans la langue du compte de l'athlète : le gabarit français compte autant. */
    @Test
    void theFrenchTemplateIsRenamedToo() throws Exception {
        importStrava("s-3", "2026-07-10", "Sortie à vélo l'après-midi", 42000, 5400)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Vélo — 42,0 km"));
    }

    /**
     * La garde essentielle. Un athlète qui prend la peine de nommer sa sortie a dit quelque chose
     * que nous n'avons pas ; l'écraser au profit du titre de la séance perdrait de l'information
     * — et sans colonne de traçabilité, la perdrait définitivement.
     */
    @Test
    void aTitleTheAthleteWroteSurvivesEvenWhenTheActivityMatches() throws Exception {
        planWorkout("2026-07-11", "Seuil 3x10'");

        importStrava("s-4", "2026-07-11", "Seuil raté, jambes lourdes", 10200, 2700)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.title").value("Seuil raté, jambes lourdes"));
    }

    /**
     * Le piège de la reconnaissance par sous-chaîne : ce titre contient « Morning Run » mot pour
     * mot, et n'a pourtant rien d'automatique.
     */
    @Test
    void aTitleThatMerelyContainsATemplateIsLeftAlone() throws Exception {
        importStrava("s-5", "2026-07-12", "Morning Run avec Paul", 10200, 2700)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Morning Run avec Paul"));
    }

    /**
     * Une saisie manuelle n'est jamais renommée, même si son titre ressemble à un gabarit : elle
     * a été tapée par quelqu'un, mot pour mot, et il n'y a aucun automatisme à corriger.
     */
    @Test
    void aManuallyEnteredActivityIsNeverRenamed() throws Exception {
        mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"MANUAL","activityDate":"2026-07-13","title":"Morning Run",
                                 "distanceM":10200,"durationS":2700,"confirmDuplicate":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Morning Run"));
    }

    // --- Utilitaires ---------------------------------------------------------------------------

    private void planWorkout(String date, String title) throws Exception {
        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"%s\",\"type\":\"ENDURANCE\",\"title\":\"%s\",\"targetDistanceM\":10000,\"targetDurationS\":2700}"
                                .formatted(date, title)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions importStrava(
            String externalId, String date, String title, int distanceM, int durationS) throws Exception {
        return mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"source":"STRAVA","externalId":"%s","activityDate":"%s","title":"%s",
                         "distanceM":%d,"durationS":%d,"confirmDuplicate":true}
                        """.formatted(externalId, date, title, distanceM, durationS)));
    }
}
