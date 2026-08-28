package com.coachrun;

import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.service.ActivityService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * « Supprimer cette sortie, et ne plus jamais l'importer. »
 *
 * <p>Supprimer ne suffisait pas : la synchro suivante rapportait la sortie, et l'athlète effaçait
 * en boucle un trajet domicile-travail, la course d'un proche partie du même téléphone, ou le
 * doublon d'une montre et d'une application enregistrant la même sortie.</p>
 *
 * <p>Ce que ces tests protègent : que le refus tienne <b>au retour</b>, pas seulement au moment du
 * clic. Une suppression qui ne survit pas à la synchro suivante n'est pas une suppression.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ActivityExclusionTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ActivityService activityService;

    private MockMvc mvc;
    private String athleteBearer;
    private String coachBearer;
    private UUID athleteId;
    private String clubId;

    private static final String STRAVA_ID = "998877665";

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode athlete = login(DemoSeedService.ATHLETE_EMAIL);
        athleteBearer = "Bearer " + athlete.get("accessToken").asText();
        athleteId = UUID.fromString(athlete.get("user").get("athleteId").asText());
        JsonNode coach = login(DemoSeedService.HEAD_COACH_EMAIL);
        coachBearer = "Bearer " + coach.get("accessToken").asText();
        clubId = coach.get("user").get("clubId").asText();
    }

    /** Le cas nominal : ce que la synchro rapportait en boucle ne revient plus. */
    @Test
    void aDeletedActivityMarkedNeverAgainIsRefusedOnReimport() throws Exception {
        String activityId = importStravaActivity().get("id").asText();

        deleteMine(activityId, true);

        // La synchro Strava écarte l'activité AVANT d'aller chercher ses détails (quota).
        assertThat(activityService.alreadyKnown(athleteId, ActivitySource.STRAVA, STRAVA_ID))
                .as("la synchro doit passer son chemin sans meme telecharger la sortie")
                .isTrue();

        // Et si la synchro la présente quand même, l'import lui-même la refuse.
        reimportStrava().andExpect(status().isConflict());
    }

    /** Une suppression ordinaire reste ordinaire : la sortie peut revenir. */
    @Test
    void aPlainDeleteLetsTheActivityComeBack() throws Exception {
        String activityId = importStravaActivity().get("id").asText();

        deleteMine(activityId, false);

        assertThat(activityService.alreadyKnown(athleteId, ActivitySource.STRAVA, STRAVA_ID))
                .as("sans case cochee, rien ne s'oppose au retour de la sortie")
                .isFalse();
        reimportStrava().andExpect(status().isCreated());
    }

    /** La sortie masquée se relit, et porte de quoi la reconnaître. */
    @Test
    void maskedActivitiesAreListedWithSomethingToRecognizeThemBy() throws Exception {
        String activityId = importStravaActivity().get("id").asText();
        deleteMine(activityId, true);

        JsonNode masked = exclusions();

        assertThat(masked).hasSize(1);
        assertThat(masked.get(0).get("title").asText()).isEqualTo("Trajet boulot");
        assertThat(masked.get(0).get("source").asText()).isEqualTo("STRAVA");
        assertThat(masked.get(0).get("activityDate").asText()).isNotBlank();
    }

    /** Et l'athlète peut revenir sur sa décision : sans recours, le masquage serait un piège. */
    @Test
    void unmaskingLetsTheActivityBeImportedAgain() throws Exception {
        String activityId = importStravaActivity().get("id").asText();
        deleteMine(activityId, true);

        String exclusionId = exclusions().get(0).get("id").asText();
        mvc.perform(delete("/me/activity-exclusions/{id}", exclusionId)
                        .header("Authorization", athleteBearer))
                .andExpect(status().isNoContent());

        assertThat(exclusions()).isEmpty();
        reimportStrava().andExpect(status().isCreated());
    }

    /**
     * Une sortie saisie à la main n'a rien à masquer : rien ne la rapporterait. Cocher la case
     * n'y crée donc aucune exclusion — en inventer une laisserait dans l'écran une ligne que rien
     * ne consultera jamais.
     */
    @Test
    void aManualActivityCreatesNoExclusion() throws Exception {
        JsonNode manual = objectMapper.readTree(mvc.perform(post("/me/activities")
                        .header("Authorization", athleteBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityDate", LocalDate.now().minusDays(2).toString(),
                                "title", "Footing saisi a la main",
                                "distanceM", 8000, "durationS", 2700))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        deleteMine(manual.get("id").asText(), true);

        assertThat(exclusions())
                .as("sans identifiant externe, il n'y a rien a empecher")
                .isEmpty();
    }

    /** Le masquage d'un athlète ne regarde que lui. */
    @Test
    void anExclusionBelongsToItsAthlete() throws Exception {
        String activityId = importStravaActivity().get("id").asText();
        deleteMine(activityId, true);
        String exclusionId = exclusions().get(0).get("id").asText();

        mvc.perform(delete("/me/activity-exclusions/{id}", exclusionId)
                        .header("Authorization", coachBearer))
                .andExpect(status().is4xxClientError());

        assertThat(exclusions()).hasSize(1);
    }

    // --- Utilitaires ---------------------------------------------------------------------------

    private String stravaPayload() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "source", "STRAVA",
                "externalId", STRAVA_ID,
                "activityDate", LocalDate.now().minusDays(1).toString(),
                "title", "Trajet boulot",
                "distanceM", 6200,
                "durationS", 1500,
                "confirmDuplicate", true));
    }

    /**
     * Le chemin d'import réel — celui qu'empruntent la synchro Strava et son webhook. Le portail
     * ({@code POST /me/activities}) est une saisie libre : il force MANUAL et ne retient aucun
     * identifiant externe, donc rien de ce qui fait l'objet de ce test.
     */
    private JsonNode importStravaActivity() throws Exception {
        return objectMapper.readTree(reimportStrava()
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions reimportStrava() throws Exception {
        return mvc.perform(post("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                .header("Authorization", coachBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(stravaPayload()));
    }

    private void deleteMine(String activityId, boolean neverImportAgain) throws Exception {
        mvc.perform(delete("/me/activities/{id}", activityId)
                        .header("Authorization", athleteBearer)
                        .param("neverImportAgain", String.valueOf(neverImportAgain)))
                .andExpect(status().isNoContent());
    }

    private JsonNode exclusions() throws Exception {
        return objectMapper.readTree(mvc.perform(get("/me/activity-exclusions")
                        .header("Authorization", athleteBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
