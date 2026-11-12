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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bibliothèque de séances course DARI Lab : arbre de catégories + structure de blocs prescrits
 * en fourchettes + calcul de toute la séance pour un athlète.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseSessionLibraryTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String bearer;
    private String clubId;
    private String athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoSeedService.HEAD_COACH_EMAIL
                                + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        bearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();

        JsonNode athletes = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes?size=50", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        athleteId = athletes.get("content").get(0).get("id").asText();
    }

    @Test
    void categoryTreeCrud() throws Exception {
        // Racine
        JsonNode root = objectMapper.readTree(mvc.perform(post("/clubs/{c}/session-categories", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vitesse\",\"discipline\":\"ROUTE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String rootId = root.get("id").asText();

        // Enfant rattaché à la racine
        JsonNode child = objectMapper.readTree(mvc.perform(post("/clubs/{c}/session-categories", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VMA\",\"parentId\":\"" + rootId + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(child.get("parentId").asText()).isEqualTo(rootId);

        JsonNode list = objectMapper.readTree(mvc.perform(get("/clubs/{c}/session-categories", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        // Le club est seedé avec ses catégories de bibliothèque : on vérifie la présence des deux
        // catégories créées ici (racine + enfant), pas un total absolu.
        List<String> catIds = new ArrayList<>();
        list.forEach(c -> catIds.add(c.get("id").asText()));
        assertThat(catIds).contains(rootId, child.get("id").asText());

        // Une catégorie ne peut pas être son propre parent.
        mvc.perform(put("/clubs/{c}/session-categories/{id}", clubId, rootId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vitesse\",\"parentId\":\"" + rootId + "\"}"))
                .andExpect(status().isConflict());

        // Suppression de la racine : l'enfant est détaché (FK SET NULL), pas d'erreur.
        mvc.perform(delete("/clubs/{c}/session-categories/{id}", clubId, rootId)
                .header("Authorization", bearer)).andExpect(status().isNoContent());
    }

    @Test
    void structureStoredAndSessionCalculatedForAthlete() throws Exception {
        // Profil + perf pour disposer d'allures VDOT.
        mvc.perform(put("/clubs/{c}/athletes/{a}/physio", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lt1Ms\":3.5,\"lt2Ms\":3.9,\"vcMs\":4.2,\"fcLt1\":148,\"fcLt2\":163,\"fcMax\":178}"))
                .andExpect(status().isOk());
        mvc.perform(post("/clubs/{c}/athletes/{a}/performances", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distance\":\"D5KM\",\"timeSeconds\":1197}"))
                .andExpect(status().isCreated());

        // Modèle de séance (CRUD existant).
        JsonNode tpl = objectMapper.readTree(mvc.perform(post("/clubs/{c}/workout-templates", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VMA 6x1000\",\"type\":\"INTERVALS\",\"title\":\"VMA\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String templateId = tpl.get("id").asText();

        // Structure DARI Lab : corps = 6 × 1000 m à 98–103 % allure 5 km, récup trot 90 s.
        String structure = """
            {"discipline":"ROUTE","favorite":true,"structure":{
              "warmup":[{"type":"easy","durationS":900,"prescription":{"ref":"PCT_LT1","minPct":75,"maxPct":88}}],
              "main":[{"type":"intervals","reps":6,"distanceM":1000,
                       "prescription":{"ref":"PCT_PACE_5KM","minPct":98,"maxPct":103},
                       "recovery":{"type":"jog","durationS":90,
                                   "prescription":{"ref":"PCT_LT1","minPct":60,"maxPct":75}}}],
              "cooldown":[{"type":"easy","durationS":600,"prescription":{"ref":"PCT_LT1","minPct":60,"maxPct":80}}]
            }}""";
        JsonNode put = objectMapper.readTree(mvc.perform(
                        put("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                                .content(structure))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(put.get("favorite").asBoolean()).isTrue();
        assertThat(put.get("structure").get("main")).hasSize(1);

        // Calcul de toute la séance pour l'athlète.
        JsonNode calc = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workout-templates/{t}/calculated", clubId, athleteId, templateId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode mainEntry = calc.get("main").get(0);
        assertThat(mainEntry.get("calc").get("computable").asBoolean()).isTrue();
        assertThat(mainEntry.get("calc").get("estimatedDistanceM").asInt()).isEqualTo(6000);
        assertThat(mainEntry.get("recoveryCalc").get("computable").asBoolean()).isTrue();
        // Totaux : au moins les 6 km du corps.
        assertThat(calc.get("totalDistanceM").asInt()).isGreaterThanOrEqualTo(6000);
        assertThat(calc.get("totalDurationS").asInt()).isGreaterThan(0);
    }

    /**
     * Séries : « 2 × (6 × 1000 m) » vaut 12 km, et double aussi les récupérations.
     *
     * <p>Un bloc à doubler n'existait pas : le coach saisissait deux blocs identiques, qu'il
     * fallait ensuite retoucher deux fois. Le volume et la durée doivent suivre le nombre de
     * séries, sans quoi les totaux de la semaine mentiraient sur la moitié de la séance.</p>
     */
    @Test
    void setsMultiplyVolumeAndRecoveries() throws Exception {
        String templateId = templateWithMain("""
              {"type":"intervals","reps":6,"distanceM":1000,"sets":2,
               "prescription":{"ref":"PCT_PACE_5KM","minPct":98,"maxPct":103},
               "recovery":{"type":"jog","durationS":90},
               "setRecovery":{"type":"jog","durationS":300}}""");

        JsonNode calc = calculated(templateId);
        assertThat(calc.get("main").get(0).get("calc").get("estimatedDistanceM").asInt()).isEqualTo(12000);

        // Récupérations : une par répétition, la dernière comprise — soit 12, dont celle qui
        // sépare les deux séries est remplacée par la récup de série. 11 × 90 s + 1 × 300 s.
        int running = calc.get("main").get(0).get("calc").get("estimatedDurationS").asInt();
        assertThat(calc.get("totalDurationS").asInt() - running).isEqualTo(11 * 90 + 300);
    }

    /**
     * La dernière répétition garde sa récupération : « 10 × 20 s côtes, récup 1'15 » en compte
     * dix, pas neuf.
     *
     * <p>Le compte s'arrêtait à la neuvième : la séance repartait avec 1'15 de moins que ce que
     * l'athlète allait courir, et l'écart grandissait à chaque bloc fractionné de la séance.
     * Une côte se termine pourtant en haut — la descente est due, y compris après la dixième.</p>
     */
    @Test
    void lastRepetitionKeepsItsRecovery() throws Exception {
        String templateId = templateWithMain("""
              {"type":"intervals","reps":10,"durationS":20,
               "prescription":{"ref":"PCT_PACE_5KM","minPct":98,"maxPct":103},
               "recovery":{"type":"jog","durationS":75}}""");

        JsonNode calc = calculated(templateId);
        int running = calc.get("main").get(0).get("calc").get("estimatedDurationS").asInt();
        assertThat(calc.get("totalDurationS").asInt() - running).isEqualTo(10 * 75);
    }

    /** Une séance écrite avant les séries n'en porte aucune, et ne doit pas bouger d'un mètre. */
    @Test
    void aBlockWithoutSetsCountsAsExactlyOne() throws Exception {
        String templateId = templateWithMain("""
              {"type":"intervals","reps":6,"distanceM":1000,
               "prescription":{"ref":"PCT_PACE_5KM","minPct":98,"maxPct":103},
               "recovery":{"type":"jog","durationS":90}}""");

        JsonNode calc = calculated(templateId);
        assertThat(calc.get("main").get(0).get("calc").get("estimatedDistanceM").asInt()).isEqualTo(6000);
        int running = calc.get("main").get(0).get("calc").get("estimatedDurationS").asInt();
        assertThat(calc.get("totalDurationS").asInt() - running).isEqualTo(6 * 90);
    }

    /**
     * L'enchaînement du terrain : « 8 × (200 m / 400 m), 100 m de récup entre chaque ».
     *
     * <p>Un bloc ne portait qu'<b>une</b> distance et qu'<b>une</b> allure. Les 200 se courent à
     * 2'48–3'04 et les 400 à 3'20–3'26 : la séance ne rentrait donc pas, et il fallait saisir
     * seize blocs à la main — puis les retoucher seize fois à chaque ajustement. Un athlète
     * courait cette séance, son coach ne pouvait pas l'écrire.</p>
     *
     * <p>Ce que le calcul doit rendre : une cible <b>par allure</b> (deux ici, aucune au niveau
     * du bloc — moyenner un 200 lancé et un 400 en résistance ne décrirait ni l'un ni l'autre),
     * et un total qui compte les huit passages, récupérations comprises.</p>
     */
    @Test
    void aChainedBlockPrescribesTwoPacesInOneRepetition() throws Exception {
        String templateId = templateWithMain("""
              {"type":"intervals","reps":8,
               "steps":[
                 {"id":"s1","distanceM":200,
                  "prescription":{"ref":"PCT_PACE_5KM","minPct":110,"maxPct":118},
                  "recovery":{"type":"jog","distanceM":100,"durationS":45}},
                 {"id":"s2","distanceM":400,
                  "prescription":{"ref":"PCT_PACE_5KM","minPct":100,"maxPct":106},
                  "recovery":{"type":"jog","distanceM":100,"durationS":45}}]}""");

        JsonNode block = calculated(templateId).get("main").get(0);

        // Une cible par allure, et aucune au niveau du bloc.
        assertThat(block.get("calc").isNull()).isTrue();
        assertThat(block.get("steps")).hasSize(2);
        assertThat(block.get("steps").get(0).get("calc").get("computable").asBoolean()).isTrue();
        assertThat(block.get("steps").get(1).get("calc").get("computable").asBoolean()).isTrue();
        // Le 200 se court plus vite que le 400 : c'est tout l'objet de l'enchaînement.
        assertThat(block.get("steps").get(0).get("calc").get("paceMinSecPerKm").asInt())
                .isLessThan(block.get("steps").get(1).get("calc").get("paceMinSecPerKm").asInt());

        JsonNode calc = calculated(templateId);
        // 8 × (200 + 400) = 4 800 m de travail. Comme pour un bloc simple, la distance des
        // récupérations n'entre pas dans le total : seule leur durée le fait.
        assertThat(calc.get("totalDistanceM").asInt()).isEqualTo(8 * 600);
        // Chaque allure est suivie de sa récup, la dernière comprise : 16 × 45 s.
        assertThat(calc.get("totalDurationS").asInt()).isGreaterThanOrEqualTo(16 * 45);
    }

    /**
     * Séries et enchaînement se combinent : « 2 × (4 × (200 / 400)) ». Entre deux séries, la
     * récup de série remplace celle qui suivait la dernière allure — elle ne s'y ajoute pas.
     */
    @Test
    void setsAndChainsCombine() throws Exception {
        String templateId = templateWithMain("""
              {"type":"intervals","reps":4,"sets":2,
               "setRecovery":{"type":"jog","durationS":300},
               "steps":[
                 {"id":"s1","distanceM":200,
                  "prescription":{"ref":"PCT_PACE_5KM","minPct":110,"maxPct":118},
                  "recovery":{"type":"jog","durationS":60}},
                 {"id":"s2","distanceM":400,
                  "prescription":{"ref":"PCT_PACE_5KM","minPct":100,"maxPct":106},
                  "recovery":{"type":"jog","durationS":60}}]}""");

        JsonNode calc = calculated(templateId);
        // 2 × 4 × (200 + 400) = 4 800 m.
        assertThat(calc.get("totalDistanceM").asInt()).isEqualTo(4800);

        // Récupérations : 16 en tout (2 séries × 4 répétitions × 2 allures), dont celle qui
        // sépare les deux séries est remplacée par les 5 minutes de récup de série.
        int running = 0;
        for (JsonNode step : calc.get("main").get(0).get("steps")) {
            running += step.get("calc").get("estimatedDurationS").asInt() * 8;
        }
        assertThat(calc.get("totalDurationS").asInt() - running).isEqualTo(15 * 60 + 300);
    }

    /** Modèle à un seul bloc de corps de séance, pour les cas de calcul ci-dessus. */
    private String templateWithMain(String mainBlockJson) throws Exception {
        mvc.perform(post("/clubs/{c}/athletes/{a}/performances", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distance\":\"D5KM\",\"timeSeconds\":1197}"))
                .andExpect(status().isCreated());
        String templateId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/workout-templates", clubId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Series " + java.util.UUID.randomUUID()
                                + "\",\"type\":\"INTERVALS\",\"title\":\"Series\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(put("/clubs/{c}/workout-templates/{t}/structure", clubId, templateId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"structure\":{\"warmup\":[],\"main\":[" + mainBlockJson + "],\"cooldown\":[]}}"))
                .andExpect(status().isOk());
        return templateId;
    }

    private JsonNode calculated(String templateId) throws Exception {
        return objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/workout-templates/{t}/calculated", clubId, athleteId, templateId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
