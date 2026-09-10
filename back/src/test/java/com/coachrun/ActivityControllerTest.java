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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ActivityControllerTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void importAutoMatchesPlannedWorkoutAndDeduplicates() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"act-%s@test.fr","password":"password123","fullName":"C","termsAccepted": true, "clubName":"AC %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String token = auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();

        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // séance prévue 10 km le 2026-07-01
        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"2026-07-01\",\"type\":\"ENDURANCE\",\"title\":\"10k\",\"targetDistanceM\":10000}"))
                .andExpect(status().isCreated());

        // import activité 10,2 km le même jour → MATCHED + delta +200
        mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"STRAVA\",\"externalId\":\"123\",\"activityDate\":\"2026-07-01\",\"distanceM\":10200,\"durationS\":2700}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.matchedWorkoutId").exists())
                .andExpect(jsonPath("$.distanceDeltaM").value(200));

        // ré-import même (source, externalId) → 409 dédup
        mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"STRAVA\",\"externalId\":\"123\",\"activityDate\":\"2026-07-01\",\"distanceM\":10200}"))
                .andExpect(status().isConflict());
    }

    /**
     * Le doublon le plus courant ne partage aucun identifiant externe : l'athlète importe la trace
     * de sa montre, puis connecte Strava — même sortie, deux provenances. La déduplication ne
     * portait que sur (athlète, source, identifiant externe), et laissait donc passer les deux.
     * Le récapitulatif hebdomadaire les additionnait ensuite.
     */
    @Test
    void nearIdenticalOutingFromAnotherSourceIsRefusedThenAcceptedOnConfirmation() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode ctx = clubWithAthlete(mvc, "dup");
        String token = ctx.get("token").asText();
        String clubId = ctx.get("clubId").asText();
        String athleteId = ctx.get("athleteId").asText();

        // Trace importée d'un fichier : 12,0 km en 60 min.
        mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"FILE\",\"activityDate\":\"2026-07-02\","
                                + "\"distanceM\":12000,\"durationS\":3600}"))
                .andExpect(status().isCreated());

        // La même sortie remontée par Strava : 12,1 km en 61 min, identifiant externe différent.
        mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"STRAVA\",\"externalId\":\"999\","
                                + "\"activityDate\":\"2026-07-02\",\"distanceM\":12100,\"durationS\":3660}"))
                .andExpect(status().isConflict());

        // Deux séances le même jour, ça existe : la confirmation passe.
        mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"STRAVA\",\"externalId\":\"999\","
                                + "\"activityDate\":\"2026-07-02\",\"distanceM\":12100,"
                                + "\"durationS\":3660,\"confirmDuplicate\":true}"))
                .andExpect(status().isCreated());
    }

    /** Une vraie deuxième séance du jour — bien plus courte — ne doit pas être confondue. */
    @Test
    void aGenuinelyDifferentSecondOutingOfTheDayIsAccepted() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode ctx = clubWithAthlete(mvc, "dup2");
        String token = ctx.get("token").asText();
        String clubId = ctx.get("clubId").asText();
        String athleteId = ctx.get("athleteId").asText();

        mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"MANUAL\",\"activityDate\":\"2026-07-03\","
                                + "\"distanceM\":18000,\"durationS\":5400}"))
                .andExpect(status().isCreated());

        // Footing de récupération le soir : 5 km en 30 min.
        mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"MANUAL\",\"activityDate\":\"2026-07-03\","
                                + "\"distanceM\":5000,\"durationS\":1800}"))
                .andExpect(status().isCreated());
    }

    /** Club + athlète fraîchement créés, renvoyés en un nœud pour alléger les tests. */
    private JsonNode clubWithAthlete(MockMvc mvc, String prefix) throws Exception {
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s-%s@test.fr","password":"password123","fullName":"C",\
                                "termsAccepted": true, "clubName":"AC %s"}
                                """.formatted(prefix, UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String token = auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        return objectMapper.createObjectNode()
                .put("token", token).put("clubId", clubId).put("athleteId", athleteId);
    }

    /**
     * La journée rapportée en bêta, rejouée dans l'ordre où elle est arrivée.
     *
     * <p>Un fractionné prescrit — « Endurance · Séance 8x(200/400) », 13,3 km en 59 min — et cinq
     * sorties le même jour : une sortie gravel, une séance de renforcement, deux footings et le
     * fractionné. C'est la <b>musculation</b> qui a emporté la séance : 29 min contre 59 prévues,
     * distance nulle lue comme « non renseignée », score 0,75 — pendant que le fractionné
     * réellement couru, dont la montre n'avait gardé que la partie rapide, restait affiché
     * « non rattachée ».</p>
     *
     * <p>Trois corrections, indissociables. Le <b>sport</b> écarte la musculation et le vélo.
     * L'exigence de <b>preuve</b> écarte le footing d'échauffement enregistré à part — 4,8 km
     * pour une séance de 13,3 : il passait le seuil sur la seule foi de la date, et emportait la
     * séance parce qu'il était importé le premier. Et le <b>titre</b> désigne le fractionné, dont
     * la montre n'avait pourtant gardé que la partie rapide.</p>
     *
     * <p>Une seule sortie doit finir rattachée : le 8*(200/400). Les quatre autres restent « non
     * rattachées » — mieux vaut rien que la mauvaise.</p>
     */
    @Test
    void theDaysBestOutingTakesTheWorkoutWhateverTheImportOrder() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode ctx = clubWithAthlete(mvc, "day");
        String token = ctx.get("token").asText();
        String clubId = ctx.get("clubId").asText();
        String athleteId = ctx.get("athleteId").asText();

        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledDate":"2026-09-08","type":"INTERVALS",
                                 "title":"Endurance · Séance 8x(200/400)",
                                 "targetDistanceM":13300,"targetDurationS":3540}"""))
                .andExpect(status().isCreated());

        // 1. La sortie gravel : jamais candidate à une séance de course à pied.
        mvc.perform(importOf(clubId, athleteId, token, "1", "Afternoon Gravel Ride", "RIDE", 11520, 1920))
                .andExpect(jsonPath("$.status").value("UNMATCHED"));

        // 2. La séance de renforcement : c'est elle qui emportait la séance prescrite.
        mvc.perform(importOf(clubId, athleteId, token, "2", "Entraînement aux poids le midi",
                        "STRENGTH", 0, 1740))
                .andExpect(jsonPath("$.status").value("UNMATCHED"));

        // 3. Le footing d'échauffement, enregistré à part : 4,8 km pour une séance de 13,3, et
        //    aucun titre qui la désigne. Il ne prend plus la séance parce qu'il arrive en
        //    premier — c'est précisément le rapprochement à côté qu'on ne veut plus.
        mvc.perform(importOf(clubId, athleteId, token, "3", "Course à pied en soirée",
                        "RUN", 4810, 1380))
                .andExpect(jsonPath("$.status").value("UNMATCHED"));

        // 4. Le fractionné : ses volumes ne concordent pas non plus (la montre n'a gardé que la
        //    partie rapide), mais son titre désigne la séance sans ambiguïté.
        mvc.perform(importOf(clubId, athleteId, token, "4", "8*(200/400)", "RUN", 6220, 1500))
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.title").value("8*(200/400)"));

        JsonNode all = objectMapper.readTree(mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        for (JsonNode a : all) {
            String expected = "8*(200/400)".equals(a.get("title").asText()) ? "MATCHED" : "UNMATCHED";
            org.assertj.core.api.Assertions.assertThat(a.get("status").asText())
                    .as("statut de « %s »", a.get("title").asText())
                    .isEqualTo(expected);
        }
    }

    /**
     * L'arbitrage entre deux sorties qui pourraient toutes deux réaliser la séance : c'est la
     * meilleure qui la prend, même arrivée en second, et la délogée repart « non rattachée ».
     *
     * <p>Sans cela, l'ordre de synchronisation trancherait à la place des chiffres — et il n'a
     * aucun rapport avec ce que l'athlète a fait.</p>
     */
    @Test
    void aBetterOutingTakesTheWorkoutFromTheOneThatArrivedFirst() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode ctx = clubWithAthlete(mvc, "arb");
        String token = ctx.get("token").asText();
        String clubId = ctx.get("clubId").asText();
        String athleteId = ctx.get("athleteId").asText();

        mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledDate":"2026-09-08","type":"ENDURANCE","title":"Footing 10 km",
                                 "targetDistanceM":10000,"targetDurationS":3000}"""))
                .andExpect(status().isCreated());

        // Une sortie plausible, mais nettement plus courte que la séance.
        String first = objectMapper.readTree(
                        mvc.perform(importOf(clubId, athleteId, token, "20", "Sortie du matin",
                                        "RUN", 6500, 1950))
                                .andExpect(jsonPath("$.status").value("MATCHED"))
                                .andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // Celle qui colle vraiment, importée après.
        mvc.perform(importOf(clubId, athleteId, token, "21", "Sortie du soir", "RUN", 10100, 3020))
                .andExpect(jsonPath("$.status").value("MATCHED"));

        JsonNode all = objectMapper.readTree(mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        for (JsonNode a : all) {
            String expected = first.equals(a.get("id").asText()) ? "UNMATCHED" : "MATCHED";
            org.assertj.core.api.Assertions.assertThat(a.get("status").asText())
                    .as("statut de « %s »", a.get("title").asText())
                    .isEqualTo(expected);
        }
    }

    /** Un rapprochement décidé à la main ne se fait pas déloger par l'import suivant. */
    @Test
    void aManualMatchSurvivesABetterCandidate() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode ctx = clubWithAthlete(mvc, "manual");
        String token = ctx.get("token").asText();
        String clubId = ctx.get("clubId").asText();
        String athleteId = ctx.get("athleteId").asText();

        String workoutId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"scheduledDate":"2026-09-08","type":"ENDURANCE","title":"Footing",
                                         "targetDistanceM":10000,"targetDurationS":3000}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        String chosen = objectMapper.readTree(
                        mvc.perform(importOf(clubId, athleteId, token, "10", "Sortie du soir",
                                        "RUN", 5000, 1500))
                                .andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // Le coach tranche : c'est cette sortie-là qui réalise la séance.
        mvc.perform(post("/clubs/{c}/athletes/{a}/activities/{id}/match/{w}",
                        clubId, athleteId, chosen, workoutId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Une sortie objectivement plus proche arrive ensuite : elle ne reprend pas la séance.
        mvc.perform(importOf(clubId, athleteId, token, "11", "Sortie", "RUN", 10050, 3010))
                .andExpect(jsonPath("$.status").value("UNMATCHED"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder importOf(
            String clubId, String athleteId, String token, String externalId, String title,
            String sport, int distanceM, int durationS) {
        return post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"source":"STRAVA","externalId":"%s","activityDate":"2026-09-08",
                         "title":"%s","sport":"%s","distanceM":%d,"durationS":%d,
                         "confirmDuplicate":true}"""
                        .formatted(externalId, title, sport, distanceM, durationS));
    }

    @Test
    void matchedActivityIsExposedOnTheWorkout() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"act3-%s@test.fr","password":"password123","fullName":"C","termsAccepted": true, "clubName":"AC3 %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String token = auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        String workoutId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"2026-07-02\",\"type\":\"ENDURANCE\",\"title\":\"10k\",\"targetDistanceM\":10000,\"targetDurationS\":2600}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"STRAVA\",\"externalId\":\"456\",\"activityDate\":\"2026-07-02\",\"distanceM\":10300,\"durationS\":2700,\"avgHr\":152}"))
                .andExpect(status().isCreated());

        // La séance expose l'activité réalisée + les écarts prévu/réalisé.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/clubs/{c}/athletes/{a}/workouts/{w}/activity", clubId, athleteId, workoutId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceM").value(10300))
                .andExpect(jsonPath("$.avgHr").value(152))
                .andExpect(jsonPath("$.matchedWorkoutId").value(workoutId))
                .andExpect(jsonPath("$.distanceDeltaM").value(300))
                .andExpect(jsonPath("$.durationDeltaS").value(100));
    }

    /**
     * Rapprochement manuel : l'algorithme se trompe, et son erreur était définitive —
     * `matchedWorkoutId` n'était modifiable que dans un sens, sans jamais rendre son statut à la
     * séance abandonnée.
     */
    @Test
    void patchMatchReassignsTheActivityAndRestoresThePreviousWorkout() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"act5-%s@test.fr","password":"password123","fullName":"C","termsAccepted": true, "clubName":"AC5 %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String token = auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // Deux séances le même jour : l'algorithme en choisit une, le coach corrige.
        String first = createWorkout(mvc, token, clubId, athleteId, "10k matin", 10000);
        String second = createWorkout(mvc, token, clubId, athleteId, "10k soir", 10000);

        String activityId = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"source\":\"STRAVA\",\"externalId\":\"789\",\"activityDate\":\"2026-07-05\""
                                        + ",\"distanceM\":10100,\"durationS\":2700}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // Réaffectation explicite à la seconde séance.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/clubs/{c}/athletes/{a}/activities/{id}/match", clubId, athleteId, activityId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workoutId\":\"" + second + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.matchedWorkoutId").value(second));

        // La séance abandonnée redevient PLANIFIÉE : sans ça, elle resterait « réalisée » à tort.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/clubs/{c}/athletes/{a}/workouts/{w}", clubId, athleteId, first)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("PLANNED"));

        // workoutId null = détachement.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/clubs/{c}/athletes/{a}/activities/{id}/match", clubId, athleteId, activityId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workoutId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNMATCHED"))
                .andExpect(jsonPath("$.matchedWorkoutId").doesNotExist());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/clubs/{c}/athletes/{a}/workouts/{w}", clubId, athleteId, second)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("PLANNED"));
    }

    private String createWorkout(MockMvc mvc, String token, String clubId, String athleteId,
                                 String title, int distanceM) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"2026-07-05\",\"type\":\"ENDURANCE\",\"title\":\"" + title
                                + "\",\"targetDistanceM\":" + distanceM + ",\"targetDurationS\":2700}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    @Test
    void workoutWithoutActivityReturnsNoContent() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"act4-%s@test.fr","password":"password123","fullName":"C","termsAccepted": true, "clubName":"AC4 %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String token = auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        String workoutId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"2026-07-03\",\"type\":\"ENDURANCE\",\"title\":\"EF\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/clubs/{c}/athletes/{a}/workouts/{w}/activity", clubId, athleteId, workoutId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void importWithoutMatchIsUnmatched() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"act2-%s@test.fr","password":"password123","fullName":"C","termsAccepted": true, "clubName":"AC2 %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String token = auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityDate\":\"2026-08-15\",\"distanceM\":8000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNMATCHED"));
    }
}
