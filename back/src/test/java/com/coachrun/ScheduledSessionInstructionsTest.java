package com.coachrun;

import com.coachrun.entity.WorkoutTemplate;
import com.coachrun.repository.WorkoutTemplateRepository;
import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Une séance posée au calendrier se modifie pour CET athlète, sans toucher la bibliothèque.
 *
 * <p><b>Ce que ces tests protègent.</b> L'écran « Adapter » affichait les notes de séance et
 * l'effort attendu mais ne les enregistrait pas : une consigne écrite pour un athlète disparaissait
 * sans un mot. Et poser une séance changeait la date de modification du modèle, alors que seul son
 * compteur d'usage bougeait — de quoi laisser croire que la bibliothèque avait été retouchée.</p>
 *
 * <p>Libellés en ASCII, comme dans {@link AdaptedSessionTest} : MockMvc lit la réponse dans le
 * jeu de caractères de la plateforme.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScheduledSessionInstructionsTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private WorkoutTemplateRepository templateRepository;
    @Autowired private EntityManager entityManager;

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

    /** Les consignes du modèle arrivent sur la séance et l'éditeur d'adaptation les relit. */
    @Test
    void thePrescriptionCarriesTheNotesCopiedFromTheTemplate() throws Exception {
        String templateId = template("Echauffement long, rester relache");
        String workoutId = schedule(templateId);

        JsonNode presc = prescription(workoutId);
        assertThat(presc.get("notes").asText()).isEqualTo("Echauffement long, rester relache");
        assertThat(presc.get("targetRpe").asInt()).isEqualTo(7);
    }

    /** Réécrire les consignes de la séance laisse celles du modèle intactes. */
    @Test
    void editingTheInstructionsLeavesTheTemplateAlone() throws Exception {
        String templateId = template("Consigne du modele");
        String workoutId = schedule(templateId);

        JsonNode updated = instructions(workoutId, "Attention au depart, genou droit", 5);
        assertThat(updated.get("notes").asText()).isEqualTo("Attention au depart, genou droit");
        assertThat(updated.get("targetRpe").asInt()).isEqualTo(5);

        JsonNode template = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(template.get("notes").asText())
                .as("le modèle garde sa propre consigne")
                .isEqualTo("Consigne du modele");
        assertThat(template.get("targetRpe").asInt()).isEqualTo(7);
    }

    /** Un texte vide efface les notes, un RPE à zéro efface l'annonce. */
    @Test
    void blankNotesAndZeroRpeClearTheInstructions() throws Exception {
        String workoutId = schedule(template("A effacer"));

        JsonNode cleared = instructions(workoutId, "  ", 0);
        assertThat(cleared.get("notes").isNull()).isTrue();
        assertThat(cleared.get("targetRpe").isNull()).isTrue();
    }

    /** Poser une séance compte un usage du modèle sans changer sa date de modification. */
    @Test
    void schedulingDoesNotTouchTheTemplateModificationDate() throws Exception {
        String templateId = template("Inchangee");
        entityManager.flush();
        entityManager.clear();
        WorkoutTemplate before = templateRepository.findById(UUID.fromString(templateId)).orElseThrow();
        Instant updatedBefore = before.getUpdatedAt();
        long versionBefore = before.getVersion();
        int usesBefore = before.getUseCount();

        schedule(templateId);
        entityManager.flush();
        entityManager.clear();

        WorkoutTemplate after = templateRepository.findById(UUID.fromString(templateId)).orElseThrow();
        assertThat(after.getUseCount()).isEqualTo(usesBefore + 1);
        assertThat(after.getLastUsedAt()).isNotNull();
        assertThat(after.getUpdatedAt()).as("le modèle n'a pas été modifié").isEqualTo(updatedBefore);
        assertThat(after.getVersion()).isEqualTo(versionBefore);
    }

    // --- Utilitaires --------------------------------------------------------------------------

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String template(String notes) throws Exception {
        String templateId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/workout-templates", clubId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"4x1000\",\"type\":\"INTERVALS\",\"title\":\"4x1000\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(put("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "notes", notes,
                                "targetRpe", 7,
                                "structure", Map.of("main", java.util.List.of(Map.of(
                                        "type", "intervals", "reps", 4, "distanceM", 1000)))))))
                .andExpect(status().isOk());
        return templateId;
    }

    private String schedule(String templateId) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workout-templates/{t}/schedule", clubId, athleteId, templateId)
                                .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"date\":\"" + LocalDate.now().plusDays(2) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    private JsonNode prescription(String workoutId) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workouts/{w}/prescription", clubId, athleteId, workoutId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode instructions(String workoutId, String notes, int rpe) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("notes", notes);
        body.put("targetRpe", rpe);
        return objectMapper.readTree(mvc.perform(
                        patch("/clubs/{c}/athletes/{a}/workouts/{w}/instructions", clubId, athleteId, workoutId)
                                .header("Authorization", coachBearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
