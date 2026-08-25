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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le mot posé sur un bloc appartient-il à l'athlète, ou à tout le monde ?
 *
 * <p>Signalé par un responsable de club : « le même commentaire apparaît pour tout le monde ».
 * Deux chemins mènent à un bloc — la séance de bibliothèque, partagée par construction, et la
 * séance d'un athlète, qui en est une copie figée. Ces tests vérifient que le second est bien
 * étanche, dans les deux sens : adapter la séance d'un athlète ne touche ni son voisin ni le
 * modèle, et retoucher le modèle ne réécrit pas ce qui est déjà au calendrier.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BlockNoteIsolationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String bearer;
    private String clubId;
    private String athleteA;
    private String athleteB;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode auth = login(DemoSeedService.HEAD_COACH_EMAIL);
        bearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();

        JsonNode athletes = objectMapper.readTree(mvc.perform(get("/clubs/{c}/athletes", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode content = athletes.get("content");
        assertThat(content.size()).as("le jeu de demonstration a au moins deux athletes")
                .isGreaterThanOrEqualTo(2);
        athleteA = content.get(0).get("id").asText();
        athleteB = content.get(1).get("id").asText();
    }

    /** Adapter la séance d'un athlète ne touche pas celle de son voisin. */
    @Test
    void aNoteWrittenOnOneAthleteSessionStaysThere() throws Exception {
        String templateId = templateWithBlock();
        String workoutA = schedule(templateId, athleteA);
        String workoutB = schedule(templateId, athleteB);

        adaptWithNote(athleteA, workoutA, "Attention au depart, tu pars trop vite");

        assertThat(noteOf(athleteA, workoutA)).isEqualTo("Attention au depart, tu pars trop vite");
        assertThat(noteOf(athleteB, workoutB))
                .as("le mot d'un athlete n'a rien a faire chez son voisin")
                .isNull();
    }

    /** Ni le modèle de bibliothèque, qui resservira à d'autres. */
    @Test
    void adaptingAnAthleteSessionLeavesTheLibraryModelAlone() throws Exception {
        String templateId = templateWithBlock();
        String workoutA = schedule(templateId, athleteA);

        adaptWithNote(athleteA, workoutA, "Consigne propre a cet athlete");

        assertThat(templateNote(templateId))
                .as("le modele de bibliotheque n'est pas le carnet d'un athlete")
                .isNull();
    }

    /** Et retoucher le modèle ne réécrit pas ce qui est déjà prescrit. */
    @Test
    void editingTheLibraryModelDoesNotRewriteScheduledSessions() throws Exception {
        String templateId = templateWithBlock();
        String workoutA = schedule(templateId, athleteA);

        putTemplateStructure(templateId, "Nouvelle consigne de modele");

        assertThat(noteOf(athleteA, workoutA))
                .as("une seance au calendrier est une copie figee")
                .isNull();
    }

    /**
     * Le chemin par lequel un mot personnel fuit vraiment : le versement en bibliothèque.
     *
     * <p>Un coach adapte la séance d'un athlète, y écrit « allure 3'47-3'42 » — un chiffre calculé
     * pour LUI — puis verse l'adaptation dans la bibliothèque pour la resservir. La consigne
     * partait avec, et reparaissait ensuite chez tous ceux à qui le modèle était prescrit.</p>
     */
    @Test
    void theNotesWrittenForOneAthleteDoNotTravelToTheLibrary() throws Exception {
        String templateId = templateWithBlock();
        String workoutA = schedule(templateId, athleteA);
        adaptWithNote(athleteA, workoutA, "allure 3'47-3'42");

        JsonNode created = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts/{w}/save-as-template", clubId, athleteA, workoutA)
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "name", "6 x 400 verse", "title", "6 x 400 verse"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        JsonNode note = created.get("structure").get("main").get(0).get("note");
        assertThat(note == null || note.isNull() ? null : note.asText())
                .as("une consigne ecrite pour un athlete n'a rien a faire dans un modele partage")
                .isNull();
        // Le reste de l'adaptation, lui, est bien versé : c'est tout l'intérêt du geste.
        assertThat(created.get("structure").get("main").get(0).get("reps").asInt()).isEqualTo(6);
    }

    /** Et la séance de l'athlète, elle, garde son mot : il n'a pas été déplacé, il est resté. */
    @Test
    void theAthleteKeepsHisOwnNoteAfterVersioning() throws Exception {
        String templateId = templateWithBlock();
        String workoutA = schedule(templateId, athleteA);
        adaptWithNote(athleteA, workoutA, "allure 3'47-3'42");

        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts/{w}/save-as-template", clubId, athleteA, workoutA)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "6 x 400 bis", "title", "6 x 400 bis"))))
                .andExpect(status().isCreated());

        assertThat(noteOf(athleteA, workoutA)).isEqualTo("allure 3'47-3'42");
    }

    // --- Utilitaires ---------------------------------------------------------------------------

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** Un modèle de bibliothèque portant un bloc, sans note. */
    private String templateWithBlock() throws Exception {
        String templateId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/workout-templates", clubId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "6 x 400", "type", "INTERVALS", "title", "6 x 400"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        putTemplateStructure(templateId, null);
        return templateId;
    }

    private void putTemplateStructure(String templateId, String note) throws Exception {
        java.util.Map<String, Object> block = new java.util.HashMap<>(Map.of(
                "id", "b1", "type", "intervals", "reps", 6, "distanceM", 400));
        block.put("note", note);
        mvc.perform(put("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("structure", Map.of(
                                "warmup", List.of(), "main", List.of(block), "cooldown", List.of())))))
                .andExpect(status().isOk());
    }

    private String schedule(String templateId, String athleteId) throws Exception {
        return objectMapper.readTree(mvc.perform(post(
                        "/clubs/{c}/athletes/{a}/workout-templates/{t}/schedule", clubId, athleteId, templateId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("date", LocalDate.now().plusDays(1).toString()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    /** Le coach adapte la séance de CET athlète et pose un mot sur le bloc. */
    private void adaptWithNote(String athleteId, String workoutId, String note) throws Exception {
        java.util.Map<String, Object> block = new java.util.HashMap<>(Map.of(
                "id", "b1", "type", "intervals", "reps", 6, "distanceM", 400));
        block.put("note", note);
        mvc.perform(put("/clubs/{c}/athletes/{a}/workouts/{w}/structure", clubId, athleteId, workoutId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "warmup", List.of(), "main", List.of(block), "cooldown", List.of()))))
                .andExpect(status().isOk());
    }

    private String noteOf(String athleteId, String workoutId) throws Exception {
        JsonNode prescription = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workouts/{w}/prescription", clubId, athleteId, workoutId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode note = prescription.get("snapshot").get("main").get(0).get("note");
        return note == null || note.isNull() ? null : note.asText();
    }

    private String templateNote(String templateId) throws Exception {
        JsonNode structure = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode main = structure.has("structure") ? structure.get("structure").get("main") : structure.get("main");
        JsonNode note = main.get(0).get("note");
        return note == null || note.isNull() ? null : note.asText();
    }
}
