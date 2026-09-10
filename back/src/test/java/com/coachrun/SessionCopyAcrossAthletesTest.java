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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Copier une séance déjà posée <b>chez un autre athlète</b> — ce que le copier-coller du
 * calendrier ne savait pas faire, et ce qui privait la vue groupe du geste tout entier.
 *
 * <p><b>Le manque.</b> {@code POST /workouts/{id}/copy} duplique une séance chez son propre
 * athlète. La grille de groupe, elle, affiche quinze athlètes côte à côte : le geste naturel y
 * est « donner à Julie ce que je viens d'écrire pour Marc », et il n'existait pas. Un coach de
 * club l'a signalé en bêta en ces termes : « sur les séances de groupe, juste les copier-coller
 * qui sont pas possible ».</p>
 *
 * <p><b>Le piège qu'on vérifie ici.</b> Recopier la séance telle quelle donnerait à la cible la
 * prescription <b>calculée pour la source</b> : des allures qui ne sont pas les siennes,
 * silencieusement. La copie inter-athlètes reprend donc la structure — les fourchettes, qui sont
 * la prescription du coach — et la recalcule pour celui qui la recevra.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SessionCopyAcrossAthletesTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String token;
    private String clubId;

    @BeforeEach
    void auth() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode a = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cp-%s@test.fr","password":"password123","fullName":"Coach CP","termsAccepted": true, "clubName":"CP %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        token = a.get("accessToken").asText();
        clubId = a.get("user").get("clubId").asText();
    }

    @Test
    void aSessionIsCopiedToAnotherAthleteOnTheChosenDay() throws Exception {
        String marc = athlete("Marc");
        String julie = athlete("Julie");
        String source = workout(marc, "2026-07-08", "6x1000");

        JsonNode copy = copyTo(julie, source, "2026-07-09");

        assertThat(copy.get("athleteId").asText()).as("la copie appartient à la cible").isEqualTo(julie);
        assertThat(copy.get("scheduledDate").asText()).isEqualTo("2026-07-09");
        // Titre volontairement sans accent ni « × » : MockMvc lit le corps de réponse dans le jeu
        // de caractères par défaut de la plateforme, et une comparaison accentuée testerait
        // l'encodage du test plutôt que le produit.
        assertThat(copy.get("title").asText()).isEqualTo("6x1000");
        assertThat(copy.get("id").asText()).as("c'est une nouvelle séance").isNotEqualTo(source);
    }

    /** La séance d'origine ne bouge pas : c'est une copie, pas un déplacement. */
    @Test
    void theSourceSessionStaysWhereItWas() throws Exception {
        String marc = athlete("Marc");
        String julie = athlete("Julie");
        String source = workout(marc, "2026-07-08", "Sortie longue");

        copyTo(julie, source, "2026-07-10");

        JsonNode original = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workouts/{w}", clubId, marc, source)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(original.get("scheduledDate").asText()).isEqualTo("2026-07-08");
        assertThat(original.get("athleteId").asText()).isEqualTo(marc);
    }

    /** La copie repart d'un état neuf : elle est à faire, pas déjà réalisée par quelqu'un d'autre. */
    @Test
    void theCopyIsPlannedAndCarriesNoFeedbackFromTheSource() throws Exception {
        String marc = athlete("Marc");
        String julie = athlete("Julie");
        String source = workout(marc, "2026-07-08", "Seuil");

        JsonNode copy = copyTo(julie, source, "2026-07-08");

        assertThat(copy.get("status").asText()).isEqualTo("PLANNED");
        assertThat(copy.hasNonNull("rpe")).isFalse();
        assertThat(copy.hasNonNull("athleteComment")).isFalse();
        assertThat(copy.hasNonNull("coachComment")).isFalse();
    }

    /** Même athlète : le chemin retombe sur la duplication existante, sans rien casser. */
    @Test
    void copyingOntoTheSameAthleteStillWorks() throws Exception {
        String marc = athlete("Marc");
        String source = workout(marc, "2026-07-08", "Footing");

        JsonNode copy = copyTo(marc, source, "2026-07-11");

        assertThat(copy.get("athleteId").asText()).isEqualTo(marc);
        assertThat(copy.get("scheduledDate").asText()).isEqualTo("2026-07-11");
    }

    // --- Utilitaires --------------------------------------------------------------------------

    private String athlete(String firstName) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + firstName + "\",\"lastName\":\"T\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    private String workout(String athleteId, String date, String title) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(java.util.Map.of(
                                        "scheduledDate", date, "type", "INTERVALS",
                                        "title", title, "targetDistanceM", 12000))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    private JsonNode copyTo(String targetAthleteId, String sourceWorkoutId, String date) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts/copy-from", clubId, targetAthleteId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(java.util.Map.of(
                                        "sourceWorkoutId", sourceWorkoutId, "scheduledDate", date))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
}
