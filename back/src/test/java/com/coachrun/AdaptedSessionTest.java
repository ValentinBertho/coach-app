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

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le geste que fait vraiment un coach : sortir un « 4 × 1000 » de sa bibliothèque, lui ajouter
 * deux répétitions pour cet athlète-là, le renommer, et garder l'adaptation.
 *
 * <p><b>Ce que ces tests protègent.</b> Signalé par un coach pilote : après adaptation, la séance
 * continuait de s'appeler « 4 × 1000 » — chez lui comme chez son athlète — parce que l'éditeur
 * d'adaptation n'enregistrait que la structure. Il ne transportait même pas le titre : rien à
 * l'écran ne permettait de le corriger, et le versement en bibliothèque proposait « Séance du
 * 20/08/2026 » plutôt que ce qu'on venait d'écrire.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdaptedSessionTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String coachBearer;
    private String clubId;
    private String athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode auth = login(DemoSeedService.HEAD_COACH_EMAIL);
        coachBearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();
        athleteId = login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText();
    }

    /**
     * Le titre voyage avec la prescription : sans lui, l'éditeur d'adaptation ne peut pas afficher
     * — et donc pas corriger — le nom de ce qu'il modifie.
     */
    @Test
    void thePrescriptionCarriesTheSessionTitle() throws Exception {
        String workoutId = plannedWorkout("4 x 1000 m");

        JsonNode prescription = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workouts/{w}/prescription", clubId, athleteId, workoutId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(prescription.get("title").asText()).isEqualTo("4 x 1000 m");
    }

    /** Renommer n'écrase pas la prescription figée — c'est tout l'intérêt d'une route dédiée. */
    @Test
    void renamingLeavesTheAdaptedStructureUntouched() throws Exception {
        String workoutId = plannedWorkout("4 x 1000 m");
        putStructure(workoutId, sixTimesThousand());

        rename(workoutId, "6 x 1000 m");

        JsonNode after = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workouts/{w}/prescription", clubId, athleteId, workoutId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(after.get("title").asText()).isEqualTo("6 x 1000 m");
        assertThat(after.get("snapshot").get("main").get(0).get("reps").asInt())
                .as("les six répétitions sont toujours là")
                .isEqualTo(6);
    }

    /** Un titre vide est refusé : une séance sans nom ne se lit ni au calendrier ni côté athlète. */
    @Test
    void anEmptyTitleIsRefused() throws Exception {
        String workoutId = plannedWorkout("4 x 1000 m");

        mvc.perform(patch("/clubs/{c}/athletes/{a}/workouts/{w}/title", clubId, athleteId, workoutId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "   "))))
                .andExpect(status().isBadRequest());
    }

    /**
     * Le parcours complet : adapter, renommer, verser. Le modèle créé doit porter la structure
     * ADAPTÉE — pas celle dont la séance était issue — sans quoi on garderait précisément ce
     * qu'on avait déjà.
     */
    @Test
    void theAdaptationIsWhatLandsInTheLibrary() throws Exception {
        String workoutId = plannedWorkout("4 x 1000 m");
        putStructure(workoutId, sixTimesThousand());
        rename(workoutId, "6 x 1000 m specifique 10 km");

        JsonNode created = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts/{w}/save-as-template", clubId, athleteId, workoutId)
                                .header("Authorization", coachBearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "name", "6 x 1000 m specifique 10 km",
                                        "title", "6 x 1000 m specifique 10 km"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(created.get("name").asText()).isEqualTo("6 x 1000 m specifique 10 km");
        assertThat(created.get("structure").get("main").get(0).get("reps").asInt())
                .as("c'est l'adaptation qui est versée, pas la séance d'origine")
                .isEqualTo(6);
    }

    /** Verser une adaptation n'altère jamais la séance de l'athlète : on copie, on ne déplace pas. */
    @Test
    void savingToTheLibraryLeavesTheAthleteSessionAlone() throws Exception {
        String workoutId = plannedWorkout("4 x 1000 m");
        putStructure(workoutId, sixTimesThousand());
        rename(workoutId, "6 x 1000 m");

        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts/{w}/save-as-template", clubId, athleteId, workoutId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Modèle versé", "title", "Modèle versé"))))
                .andExpect(status().isCreated());

        JsonNode after = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workouts/{w}/prescription", clubId, athleteId, workoutId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(after.get("title").asText())
                .as("la séance de l'athlète garde son nom")
                .isEqualTo("6 x 1000 m");
    }

    // --- Utilitaires --------------------------------------------------------------------------
    //
    // Les libellés sont volontairement en ASCII (« 4 x 1000 » et non « 4 × 1000 ») : MockMvc lit
    // le corps de réponse dans le jeu de caractères par défaut de la plateforme, si bien qu'une
    // comparaison sur un titre accentué éprouverait l'encodage du harnais de test plutôt que le
    // comportement du produit. Le chemin des accents est couvert ailleurs, sur le contenu.

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String plannedWorkout(String title) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", coachBearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "scheduledDate", LocalDate.now().toString(),
                                        "type", "INTERVALS",
                                        "title", title,
                                        "targetDurationS", 3600))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    /** La séance adaptée : six répétitions là où le modèle en portait quatre. */
    private Map<String, Object> sixTimesThousand() {
        return Map.of(
                "warmup", java.util.List.of(),
                "main", java.util.List.of(Map.of(
                        "id", "b1", "type", "intervals", "reps", 6, "distanceM", 1000)),
                "cooldown", java.util.List.of());
    }

    private void putStructure(String workoutId, Map<String, Object> structure) throws Exception {
        mvc.perform(put("/clubs/{c}/athletes/{a}/workouts/{w}/structure", clubId, athleteId, workoutId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(structure)))
                .andExpect(status().isOk());
    }

    private void rename(String workoutId, String title) throws Exception {
        mvc.perform(patch("/clubs/{c}/athletes/{a}/workouts/{w}/title", clubId, athleteId, workoutId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", title))))
                .andExpect(status().isOk());
    }
}
