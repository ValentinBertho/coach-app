package com.coachrun;

import com.coachrun.integration.StravaClient;
import com.coachrun.integration.StravaClient.StravaActivity;
import com.coachrun.integration.StravaClient.StravaAthlete;
import com.coachrun.integration.StravaClient.TokenResponse;
import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Écrire dans le compte Strava de quelqu'un.
 *
 * <p>Darilab remplace déjà, à l'import, les titres que Strava compose lui-même. Répercuter ce
 * nouveau nom <b>sur Strava</b> est d'une tout autre nature : c'est une écriture sur le compte
 * personnel de l'athlète, visible de ses abonnés, et dont nous ne gardons pas le nom d'origine —
 * donc irréversible de notre côté.</p>
 *
 * <p>Ces tests ne vérifient pas tant que l'écriture marche : ils vérifient qu'elle <b>n'a pas
 * lieu</b>. Trois verrous doivent tomber ensemble — un titre effectivement remplacé, le
 * consentement de l'athlète, et le scope accordé par Strava — et chacun est testé seul, parce que
 * c'est chacun seul qui protège un compte qui ne nous appartient pas.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
// @MockBean force un contexte dédié : base mémoire propre, comme StravaControllerTest.
@org.springframework.test.context.TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:strava-rename;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class StravaRenameOnStravaTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.coachrun.security.OAuthStateCodec stateCodec;
    @MockBean private StravaClient stravaClient;

    private MockMvc mvc;
    private String coachBearer;
    private String athleteBearer;
    private String clubId;
    private String athleteId;

    /** Une date largement hors du jeu de démonstration : aucune séance ne peut être rapprochée. */
    private static final String NO_WORKOUT_DAY = "2025-11-05T09:00:00Z";

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode coach = login(DemoSeedService.HEAD_COACH_EMAIL);
        coachBearer = "Bearer " + coach.get("accessToken").asText();
        clubId = coach.get("user").get("clubId").asText();
        JsonNode athlete = login(DemoSeedService.ATHLETE_EMAIL);
        athleteBearer = "Bearer " + athlete.get("accessToken").asText();
        athleteId = athlete.get("user").get("athleteId").asText();
        when(stravaClient.isConfigured()).thenReturn(true);
    }

    /**
     * Le défaut, et il n'est pas négociable : personne n'hérite d'une écriture qu'il n'a pas
     * demandée, même quand Strava nous en a donné le droit.
     */
    @Test
    void nothingIsWrittenToStravaWithoutTheAthleteAskingForIt() throws Exception {
        connectWithScope("activity:read_all,activity:write");
        givenStravaReturns(3001L, "Morning Run");

        assertThat(importNow()).isEqualTo(1);

        verify(stravaClient, never()).renameActivity(anyString(), anyLong(), anyString());
    }

    /**
     * Le consentement dans Darilab ne vaut pas autorisation chez Strava. Un athlète connecté avant
     * que nous ne demandions {@code activity:write} peut cocher la case : son jeton, lui, ne porte
     * pas le droit d'écrire, et il faut le dire plutôt que d'échouer en silence.
     */
    @Test
    void theConsentAloneIsNotEnoughWhenStravaGrantedNoWriteAccess() throws Exception {
        connectWithScope("activity:read_all");

        JsonNode status = objectMapper.readTree(setRenameOnStrava(true));
        assertThat(status.get("renameOnStrava").asBoolean())
                .as("le consentement de l'athlète est enregistré tel qu'il l'a donné")
                .isTrue();
        assertThat(status.get("canRenameOnStrava").asBoolean())
                .as("mais Strava ne nous a pas accordé l'écriture : l'écran doit pouvoir le dire")
                .isFalse();

        givenStravaReturns(3002L, "Morning Run");
        assertThat(importNow()).isEqualTo(1);

        verify(stravaClient, never()).renameActivity(anyString(), anyLong(), anyString());
    }

    /** Les trois verrous tombés, le nom retenu par Darilab remonte sur Strava. */
    @Test
    void withConsentAndScopeTheNewTitleIsPushedToStrava() throws Exception {
        connectWithScope("activity:read_all,activity:write");
        setRenameOnStrava(true);
        givenStravaReturns(3003L, "Morning Run");
        when(stravaClient.renameActivity(anyString(), anyLong(), anyString())).thenReturn(true);

        assertThat(importNow()).isEqualTo(1);

        ArgumentCaptor<String> pushed = ArgumentCaptor.forClass(String.class);
        verify(stravaClient).renameActivity(anyString(), eq(3003L), pushed.capture());
        assertThat(pushed.getValue())
                .as("sans séance en face, le titre descriptif composé à l'import")
                .isEqualTo("Course à pied — 12,0 km");
        assertThat(pushed.getValue())
                .as("et c'est exactement ce que l'athlète lit dans Darilab")
                .isEqualTo(titleOfImportedActivity());
    }

    /**
     * La garde qui compte le plus ici. Un nom que l'athlète a écrit n'est pas remplacé localement ;
     * il n'a donc rien à remonter, et surtout rien à écraser chez lui.
     */
    @Test
    void aTitleTheAthleteWroteIsNeverPushedBack() throws Exception {
        connectWithScope("activity:read_all,activity:write");
        setRenameOnStrava(true);
        givenStravaReturns(3004L, "Sortie longue avec Paul");

        assertThat(importNow()).isEqualTo(1);

        verify(stravaClient, never()).renameActivity(anyString(), anyLong(), anyString());
    }

    /**
     * Strava est le miroir, pas la source : le titre juste est déjà en base. Un refus d'écriture —
     * quota atteint, jeton révoqué — ne doit donc coûter ni l'import ni la synchro.
     */
    @Test
    void aRefusedWriteCostsNothingToTheImport() throws Exception {
        connectWithScope("activity:read_all,activity:write");
        setRenameOnStrava(true);
        givenStravaReturns(3005L, "Morning Run");
        when(stravaClient.renameActivity(anyString(), anyLong(), anyString())).thenReturn(false);

        assertThat(importNow())
                .as("l'activité est importée, renommée localement, et le refus reste sans suite")
                .isEqualTo(1);
        assertThat(titleOfImportedActivity()).isEqualTo("Course à pied — 12,0 km");
    }

    /** Retirer son consentement suffit à arrêter les écritures suivantes. */
    @Test
    void withdrawingTheConsentStopsFurtherWrites() throws Exception {
        connectWithScope("activity:read_all,activity:write");
        setRenameOnStrava(true);
        JsonNode off = objectMapper.readTree(setRenameOnStrava(false));
        assertThat(off.get("renameOnStrava").asBoolean()).isFalse();

        givenStravaReturns(3006L, "Morning Run");
        assertThat(importNow()).isEqualTo(1);

        verify(stravaClient, never()).renameActivity(anyString(), anyLong(), anyString());
    }

    // --- Utilitaires ---------------------------------------------------------------------------

    private void connectWithScope(String grantedScope) throws Exception {
        when(stravaClient.exchangeCode("auth-code")).thenReturn(new TokenResponse(
                "acc-1", "ref-1", Instant.now().getEpochSecond() + 3600, grantedScope,
                new StravaAthlete(99L)));
        String state = stateCodec.issue(UUID.fromString(athleteId));
        mvc.perform(post("/clubs/{c}/athletes/{a}/strava/connect", clubId, athleteId)
                        .header("Authorization", coachBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"auth-code\",\"state\":\"" + state + "\"}"))
                .andExpect(status().isOk());
    }

    private String setRenameOnStrava(boolean enabled) throws Exception {
        return mvc.perform(put("/me/strava/rename-on-strava")
                        .header("Authorization", athleteBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":" + enabled + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void givenStravaReturns(long id, String name) {
        when(stravaClient.listActivities(anyString(), anyLong()))
                .thenReturn(List.of(stravaActivity(id, name)));
    }

    private int importNow() throws Exception {
        return objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/strava/import", clubId, athleteId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("imported").asInt();
    }

    /**
     * Le titre tel que Darilab l'a retenu, relu par l'API — pas celui qu'on croit avoir écrit.
     *
     * <p>Le corps est décodé en UTF-8 explicitement : MockMvc, faute de charset déclaré sur la
     * réponse, retomberait sur ISO-8859-1 et rendrait « Course Ã  pied ». Les octets, eux, sont
     * corrects — c'est la relecture qui se tromperait, et l'assertion accuserait le produit.</p>
     */
    private String titleOfImportedActivity() throws Exception {
        JsonNode activities = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/activities", clubId, athleteId)
                                .header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        for (JsonNode a : activities) {
            if (NO_WORKOUT_DAY.startsWith(a.get("activityDate").asText())) {
                return a.get("title").asText();
            }
        }
        throw new AssertionError("activité importée introuvable");
    }

    private static StravaActivity stravaActivity(long id, String name) {
        return new StravaActivity(id, name, "Run", 12000.0, 3600, 120.0,
                145.0, 178.0, 3.3, 88.0, 240.0, 620.0, 90.0, "b123", 0, 1,
                null, NO_WORKOUT_DAY, NO_WORKOUT_DAY + "Z");
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
