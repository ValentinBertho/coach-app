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
 * Travailler une séance de préparation physique <b>sur le calendrier</b> : la poser à blanc, en
 * modifier le contenu, la copier d'un jour ou d'un athlète à l'autre, la verser en bibliothèque.
 *
 * <p><b>Le manque.</b> Une séance de force planifiée était figée : son snapshot ne s'écrivait
 * qu'à l'assignation. Changer une série supposait donc de déprogrammer, retoucher le modèle de
 * bibliothèque — qui sert d'autres athlètes — puis replanifier ; en pratique, les coachs créaient
 * une séance de plus à chaque ajustement. Un coach de la bêta l'a dit ainsi : « pouvoir modifier
 * le contenu directement sans créer une nouvelle séance ». La course avait ces gestes depuis
 * toujours ; la force, aucun.</p>
 *
 * <p>Ce fichier protège les quatre : l'édition en place, la copie fidèle (snapshot, pas modèle),
 * la copie inter-athlètes qui <b>recalcule</b> les charges, et le versement en bibliothèque.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class StrengthPlannedEditingTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.coachrun.repository.ScheduledStrengthSessionRepository scheduledRepository;

    private MockMvc mvc;
    private String token;
    private String clubId;
    private String exerciseId;

    @BeforeEach
    void auth() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode a = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pp-%s@test.fr","password":"password123","fullName":"Coach PP","termsAccepted": true, "clubName":"PP %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        token = a.get("accessToken").asText();
        clubId = a.get("user").get("clubId").asText();
        exerciseId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/pp/exercises", clubId)
                        .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Squat\",\"category\":\"FORCE_MAX\",\"muscleGroups\":[\"QUADRICEPS\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    /** Le geste central : réécrire le contenu d'une séance déjà posée, sans en créer une autre. */
    @Test
    void thePlannedSessionContentIsRewrittenInPlace() throws Exception {
        String athleteId = athlete("Marc", 100);
        String sessionId = librarySession("Force max", 4, 5);
        String scheduledId = schedule(athleteId, sessionId, "2026-06-25");

        JsonNode rx = putStructure(athleteId, scheduledId, 6, 3);

        assertThat(rx.get("snapshot").get("blocks").get(0).get("exercises").get(0)
                .get("prescription").get("sets").asInt()).isEqualTo(6);
        // Les charges suivent : elles sont recalculées avec le 1RM de l'athlète, pas recopiées.
        JsonNode charge = rx.get("calculated").get("blocks").get(0).get("exercises").get(0).get("charge");
        assertThat(charge.get("kgMin").asDouble()).isEqualTo(80.0);
        assertThat(prescription(athleteId, scheduledId).get("snapshot").get("blocks").get(0)
                .get("exercises").get(0).get("prescription").get("sets").asInt())
                .as("la modification est bien écrite, pas seulement renvoyée").isEqualTo(6);
    }

    /** Adapter la séance de quelqu'un ne doit rien changer chez les autres : le modèle est intact. */
    @Test
    void editingAPlannedSessionLeavesTheLibraryModelUntouched() throws Exception {
        String athleteId = athlete("Marc", 100);
        String sessionId = librarySession("Force max", 4, 5);
        String scheduledId = schedule(athleteId, sessionId, "2026-06-25");

        putStructure(athleteId, scheduledId, 6, 3);

        JsonNode model = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/pp/sessions/{s}", clubId, sessionId).header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(model.get("structure").get("blocks").get(0).get("exercises").get(0)
                .get("prescription").get("sets").asInt()).isEqualTo(4);
    }

    /**
     * Le copier-coller recopie la séance <b>affichée</b>. Il repassait par le modèle de
     * bibliothèque : coller une séance adaptée rendait sa version d'origine, en silence.
     */
    @Test
    void copyingAPlannedSessionKeepsTheAdaptationRatherThanTheModel() throws Exception {
        String athleteId = athlete("Marc", 100);
        String sessionId = librarySession("Force max", 4, 5);
        String scheduledId = schedule(athleteId, sessionId, "2026-06-25");
        putStructure(athleteId, scheduledId, 6, 3);

        String copyId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/pp/scheduled/{s}/copy", clubId, athleteId, scheduledId)
                                .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scheduledDate\":\"2026-07-02\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        assertThat(copyId).isNotEqualTo(scheduledId);
        assertThat(prescription(athleteId, copyId).get("snapshot").get("blocks").get(0)
                .get("exercises").get(0).get("prescription").get("sets").asInt()).isEqualTo(6);
    }

    /** Une séance posée sans modèle se copie quand même : elle n'a plus besoin d'en avoir un. */
    @Test
    void aSessionBuiltOnTheCalendarCanStillBeCopied() throws Exception {
        String athleteId = athlete("Marc", 100);
        String scheduledId = adHoc(athleteId, "2026-06-25");
        putStructure(athleteId, scheduledId, 3, 10);

        mvc.perform(post("/clubs/{c}/athletes/{a}/pp/scheduled/{s}/copy", clubId, athleteId, scheduledId)
                        .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"2026-06-27\"}"))
                .andExpect(status().isCreated());
    }

    /**
     * Copiée chez quelqu'un d'autre, la séance garde la structure du coach mais reçoit
     * <b>les charges de celui qui la fera</b> : donner à Julie les kilos de Marc serait faux, et
     * faux silencieusement.
     */
    @Test
    void copyingToAnotherAthleteRecomputesTheCharges() throws Exception {
        String marc = athlete("Marc", 100);
        String julie = athlete("Julie", 60);
        String sessionId = librarySession("Force max", 4, 5);
        String scheduledId = schedule(marc, sessionId, "2026-06-25");

        JsonNode copy = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/pp/scheduled/copy-from", clubId, julie)
                                .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "sourceScheduledId", scheduledId, "scheduledDate", "2026-06-26"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(copy.get("athleteId").asText()).isEqualTo(julie);
        JsonNode charge = prescription(julie, copy.get("id").asText())
                .get("calculated").get("blocks").get(0).get("exercises").get(0).get("charge");
        // 80 % de 60 kg, pas de 100 : la fourchette est celle de Julie.
        assertThat(charge.get("kgMin").asDouble()).isEqualTo(47.5);
    }

    /** Une séance improvisée au calendrier se verse en bibliothèque, sans être reconstruite. */
    @Test
    void aSessionBuiltOnTheCalendarIsSavedToTheLibrary() throws Exception {
        String athleteId = athlete("Marc", 100);
        String scheduledId = adHoc(athleteId, "2026-06-25");
        putStructure(athleteId, scheduledId, 5, 5);

        JsonNode created = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/pp/scheduled/{s}/save-as-session", clubId, athleteId, scheduledId)
                                .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Full body improvise\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(created.get("name").asText()).isEqualTo("Full body improvise");
        assertThat(created.get("structure").get("blocks").get(0).get("exercises").get(0)
                .get("prescription").get("sets").asInt()).isEqualTo(5);
        // Le commentaire écrit pour cet athlète-là ne devient pas une consigne de club.
        assertThat(created.get("structure").get("blocks").get(0).get("exercises").get(0)
                .hasNonNull("coachNotes")).isFalse();
    }

    /** Renommer la séance de l'athlète ne touche ni son contenu ni le modèle dont elle vient. */
    @Test
    void aPlannedSessionIsRenamedWithoutTouchingItsPrescription() throws Exception {
        String athleteId = athlete("Marc", 100);
        String sessionId = librarySession("Force max", 4, 5);
        String scheduledId = schedule(athleteId, sessionId, "2026-06-25");

        JsonNode renamed = objectMapper.readTree(mvc.perform(
                        patch("/clubs/{c}/athletes/{a}/pp/scheduled/{s}/title", clubId, athleteId, scheduledId)
                                .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Force max - bas du corps\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(renamed.get("title").asText()).isEqualTo("Force max - bas du corps");
        assertThat(prescription(athleteId, scheduledId).get("snapshot").get("blocks")).hasSize(1);
    }

    /** La séance vierge naît sur son jour, prête à être remplie — sans passer par la bibliothèque. */
    @Test
    void anEmptySessionIsPlacedDirectlyOnTheCalendar() throws Exception {
        String athleteId = athlete("Marc", 100);

        JsonNode created = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/pp/scheduled", clubId, athleteId)
                                .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"date\":\"2026-06-25\",\"fieldsPreset\":\"AVANCE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(created.get("scheduledDate").asText()).isEqualTo("2026-06-25");
        assertThat(created.hasNonNull("sourceSessionId")).as("aucun modèle derrière").isFalse();
        assertThat(prescription(athleteId, created.get("id").asText()).get("snapshot").get("blocks")).isEmpty();
    }

    /**
     * Une séance déjà faite garde son contenu : le réécrire fausserait la comparaison prévu /
     * réalisé, l'athlète ayant soulevé ce qui était prescrit ce jour-là. Même garde que sur le
     * décalage de charge.
     */
    @Test
    void aCompletedSessionIsNoLongerRewritten() throws Exception {
        String athleteId = athlete("Marc", 100);
        String sessionId = librarySession("Force max", 4, 5);
        String scheduledId = schedule(athleteId, sessionId, "2026-06-25");
        // La séance est close par le débrief de l'athlète ; on pose l'état ici, le portail
        // athlète ayant sa propre authentification et n'étant pas le sujet de ce test.
        var done = scheduledRepository.findById(UUID.fromString(scheduledId)).orElseThrow();
        done.setCompleted(true);
        scheduledRepository.save(done);

        mvc.perform(put("/clubs/{c}/athletes/{a}/pp/scheduled/{s}/structure", clubId, athleteId, scheduledId)
                        .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(structureJson(9, 9)))
                .andExpect(status().isConflict());
    }

    // --- Utilitaires --------------------------------------------------------------------------

    private String bearer() {
        return "Bearer " + token;
    }

    /** Un athlète du club, avec son 1RM de squat — sans lui, aucune charge n'est calculable. */
    private String athlete(String firstName, int oneRmKg) throws Exception {
        String id = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + firstName + "\",\"lastName\":\"T\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(put("/clubs/{c}/athletes/{a}/pp/1rm", clubId, id)
                        .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"" + exerciseId + "\",\"rmKg\":" + oneRmKg + "}"))
                .andExpect(status().isOk());
        return id;
    }

    private String librarySession(String name, int sets, int reps) throws Exception {
        String id = objectMapper.readTree(mvc.perform(post("/clubs/{c}/pp/sessions", clubId)
                        .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(put("/clubs/{c}/pp/sessions/{s}/structure", clubId, id)
                        .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(structureJson(sets, reps)))
                .andExpect(status().isOk());
        return id;
    }

    private String schedule(String athleteId, String sessionId, String date) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/pp/sessions/{s}/schedule", clubId, athleteId, sessionId)
                                .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"date\":\"" + date + "\",\"fieldsPreset\":\"AVANCE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    private String adHoc(String athleteId, String date) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/pp/scheduled", clubId, athleteId)
                                .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"date\":\"" + date + "\",\"fieldsPreset\":\"AVANCE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    private JsonNode putStructure(String athleteId, String scheduledId, int sets, int reps) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        put("/clubs/{c}/athletes/{a}/pp/scheduled/{s}/structure", clubId, athleteId, scheduledId)
                                .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                                .content(structureJson(sets, reps)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode prescription(String athleteId, String scheduledId) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/pp/scheduled/{s}/prescription", clubId, athleteId, scheduledId)
                                .header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** Un bloc, un exercice prescrit en % du 1RM : de quoi voir bouger la charge calculée. */
    private String structureJson(int sets, int reps) {
        return """
            {"structure":{"blocks":[
              {"id":"b1","blockType":"PRINCIPAL","format":"CLASSIQUE","exercises":[
                {"exerciseId":"%s","exerciseName":"Squat","setType":"STANDARD",
                 "coachNotes":"Garde le dos droit",
                 "prescription":{"chargeRefType":"PCT_RM_RANGE","chargePctRmMin":80,"chargePctRmMax":85,
                                 "sets":%d,"repsFixed":%d}}]}]}}"""
                .formatted(exerciseId, sets, reps);
    }
}
