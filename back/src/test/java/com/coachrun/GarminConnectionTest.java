package com.coachrun;

import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.integration.GarminClient;
import com.coachrun.integration.GarminClient.TokenResponse;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.security.OAuthStateCodec;
import com.coachrun.service.DemoSeedService;
import com.coachrun.service.GarminService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Connexion Garmin Connect d'un athlète (OAuth 2.0 PKCE) : consentement, échange du code,
 * permissions, déconnexion depuis Darilab ou depuis Garmin.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
// @MockBean force un contexte dédié : base mémoire propre, comme StravaControllerTest.
@org.springframework.test.context.TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:garmin-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class GarminConnectionTest {

    private static final String GARMIN_USER = "d3315b1072421d0dd7c8f6b8e1de4df8";

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OAuthStateCodec stateCodec;
    @Autowired private GarminService garminService;
    @Autowired private DeviceConnectionRepository connectionRepository;
    @MockBean private GarminClient garminClient;

    private MockMvc mvc;
    private String athleteBearer;
    private UUID athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode athlete = login(DemoSeedService.ATHLETE_EMAIL);
        athleteBearer = "Bearer " + athlete.get("accessToken").asText();
        athleteId = UUID.fromString(athlete.get("user").get("athleteId").asText());
        when(garminClient.isConfigured()).thenReturn(true);
        when(garminClient.clientId()).thenReturn("client-garmin");
        when(garminClient.redirectUri()).thenReturn("https://www.darilab.app/app/garmin/callback");
    }

    @Test
    void lUrlDeConsentementPorteUnChallengePkceDeriveDuState() throws Exception {
        String url = json(mvc.perform(get("/me/garmin/authorize").header("Authorization", athleteBearer))
                .andExpect(status().isOk())).get("url").asText();

        var params = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
        assertThat(url).startsWith(GarminClient.AUTHORIZE_URL);
        assertThat(params.getFirst("client_id")).isEqualTo("client-garmin");
        assertThat(params.getFirst("code_challenge_method")).isEqualTo("S256");
        String state = params.getFirst("state");
        assertThat(state).startsWith(athleteId.toString());
        assertThat(params.getFirst("code_challenge"))
                .isEqualTo(OAuthStateCodec.pkceChallenge(stateCodec.pkceVerifier(state)));
    }

    @Test
    void connecterEnregistreLeCompteEtLesPermissions() throws Exception {
        connect(List.of("ACTIVITY_EXPORT", "WORKOUT_IMPORT"));

        JsonNode st = json(mvc.perform(get("/me/garmin").header("Authorization", athleteBearer))
                .andExpect(status().isOk()));
        assertThat(st.get("connected").asBoolean()).isTrue();
        assertThat(st.get("providerUserId").asText()).isEqualTo(GARMIN_USER);
        assertThat(st.get("canSendWorkouts").asBoolean()).isTrue();
        assertThat(st.get("canImportActivities").asBoolean()).isTrue();

        var conn = connectionRepository.findByAthleteIdAndProvider(athleteId, DeviceProvider.GARMIN).orElseThrow();
        assertThat(conn.getAccessToken()).isEqualTo(ACCESS);
        assertThat(conn.getRefreshToken()).isEqualTo(REFRESH);
    }

    @Test
    void unePermissionRefuseeAuConsentementResteRefusee() throws Exception {
        connect(List.of("ACTIVITY_EXPORT"));

        JsonNode st = json(mvc.perform(get("/me/garmin").header("Authorization", athleteBearer)));
        assertThat(st.get("canSendWorkouts").asBoolean()).isFalse();
        assertThat(st.get("canImportActivities").asBoolean()).isTrue();
    }

    @Test
    void unStateEmisPourUnAutreAthleteEstRefuse() throws Exception {
        String foreign = stateCodec.issue(UUID.randomUUID());
        mvc.perform(post("/me/garmin/connect").header("Authorization", athleteBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"c\",\"state\":\"" + foreign + "\"}"))
                .andExpect(status().isBadRequest());
        verify(garminClient, never()).exchangeCode(anyString(), anyString());
    }

    @Test
    void seDeconnecterRetireAussiLEnregistrementChezGarmin() throws Exception {
        connect(List.of("WORKOUT_IMPORT"));
        when(garminClient.deleteRegistration(ACCESS)).thenReturn(true);

        mvc.perform(delete("/me/garmin").header("Authorization", athleteBearer))
                .andExpect(status().isNoContent());

        verify(garminClient).deleteRegistration(ACCESS);
        assertThat(connectionRepository.findByAthleteIdAndProvider(athleteId, DeviceProvider.GARMIN)).isEmpty();
    }

    @Test
    void uneDesinscriptionVenueDeGarminRetireLaConnexionSansRappelerGarmin() throws Exception {
        connect(List.of("WORKOUT_IMPORT"));

        assertThat(garminService.handleDeregistration(GARMIN_USER)).isEqualTo(1);

        assertThat(connectionRepository.findByAthleteIdAndProvider(athleteId, DeviceProvider.GARMIN)).isEmpty();
        verify(garminClient, never()).deleteRegistration(anyString());
    }

    @Test
    void unChangementDePermissionsChezGarminEstSuivi() throws Exception {
        connect(List.of("ACTIVITY_EXPORT", "WORKOUT_IMPORT"));

        garminService.handlePermissionsChange(GARMIN_USER, List.of("ACTIVITY_EXPORT"));

        JsonNode st = json(mvc.perform(get("/me/garmin").header("Authorization", athleteBearer)));
        assertThat(st.get("canSendWorkouts").asBoolean()).isFalse();
    }

    @Test
    void unCompteGarminInconnuNeTouchePersonne() {
        assertThat(garminService.handleDeregistration("inconnu")).isZero();
    }

    /**
     * Des jetons de la taille de ceux de Garmin : des JWT de plus de mille caractères, plus
     * longs encore une fois chiffrés en base. Avec « acc-1 », le test passait alors que la
     * vraie connexion échouait (colonnes VARCHAR(512) dépassées → 409).
     */
    private static final String ACCESS = "eyJhbGciOiJSUzI1NiJ9." + "a".repeat(1400) + ".sig";
    private static final String REFRESH = "eyJyZWZyZXNoIjp0cnVlfQ." + "r".repeat(900) + ".sig";

    private void connect(List<String> permissions) throws Exception {
        String state = stateCodec.issue(athleteId);
        when(garminClient.exchangeCode(eq("auth-code"), eq(stateCodec.pkceVerifier(state))))
                .thenReturn(new TokenResponse(ACCESS, REFRESH, 86400L, 7775998L, "PARTNER_READ"));
        when(garminClient.userId(ACCESS)).thenReturn(GARMIN_USER);
        when(garminClient.permissions(ACCESS)).thenReturn(permissions);
        mvc.perform(post("/me/garmin/connect").header("Authorization", athleteBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"auth-code\",\"state\":\"" + state + "\"}"))
                .andExpect(status().isOk());
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
