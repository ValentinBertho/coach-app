package com.coachrun;

import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.integration.CorosMcpClient;
import com.coachrun.integration.CorosMcpClient.Endpoints;
import com.coachrun.integration.CorosMcpClient.TokenResponse;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.repository.OAuthClientRegistrationRepository;
import com.coachrun.security.OAuthStateCodec;
import com.coachrun.service.CorosService;
import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Connexion COROS d'un athlète (COROS MCP, OAuth 2.0 PKCE avec client enregistré
 * dynamiquement) : consentement, échange du code, rafraîchissement, déconnexion.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@org.springframework.test.context.TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:coros-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class CorosConnectionTest {

    private static final String REDIRECT = "https://www.darilab.app/app/coros/callback";
    private static final Endpoints EU = new Endpoints("https://mcpeu.coros.com",
            "https://mcpeu.coros.com/oauth2/authorize", "https://mcpeu.coros.com/oauth2/token",
            "https://mcpeu.coros.com/connect/register", null, "https://mcpeu.coros.com/mcp");

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OAuthStateCodec stateCodec;
    @Autowired private CorosService corosService;
    @Autowired private DeviceConnectionRepository connectionRepository;
    @Autowired private OAuthClientRegistrationRepository registrationRepository;
    @MockBean private CorosMcpClient corosClient;

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
        when(corosClient.isConfigured()).thenReturn(true);
        when(corosClient.redirectUri()).thenReturn(REDIRECT);
        when(corosClient.endpoints()).thenReturn(EU);
        when(corosClient.endpointsFor("https://mcpeu.coros.com")).thenReturn(EU);
        when(corosClient.registerClient(EU)).thenReturn("client-coros");
        when(corosClient.authorizeUrl(any(), anyString(), anyString(), anyString())).thenAnswer(inv ->
                UriComponentsBuilder.fromHttpUrl(EU.authorization())
                        .queryParam("client_id", (String) inv.getArgument(1))
                        .queryParam("code_challenge", (String) inv.getArgument(2))
                        .queryParam("state", (String) inv.getArgument(3))
                        .build().toUriString());
    }

    @Test
    void leClientEstEnregistreUneSeuleFoisEtLeChallengeDeriveDuState() throws Exception {
        String url = authorizeUrl();
        authorizeUrl();

        verify(corosClient, times(1)).registerClient(EU);
        assertThat(registrationRepository.findAll())
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getProvider()).isEqualTo(DeviceProvider.COROS);
                    assertThat(r.getIssuer()).isEqualTo("https://mcpeu.coros.com");
                    assertThat(r.getRedirectUri()).isEqualTo(REDIRECT);
                    assertThat(r.getClientId()).isEqualTo("client-coros");
                });

        var params = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
        assertThat(params.getFirst("client_id")).isEqualTo("client-coros");
        String state = params.getFirst("state");
        assertThat(state).startsWith(athleteId.toString());
        assertThat(params.getFirst("code_challenge"))
                .isEqualTo(OAuthStateCodec.pkceChallenge(stateCodec.pkceVerifier(state)));
    }

    @Test
    void connecterEnregistreLeServeurRegionalLeClientEtLIdentifiantCoros() throws Exception {
        connect();

        JsonNode st = json(mvc.perform(get("/me/coros").header("Authorization", athleteBearer))
                .andExpect(status().isOk()));
        assertThat(st.get("configured").asBoolean()).isTrue();
        assertThat(st.get("connected").asBoolean()).isTrue();
        assertThat(st.get("providerUserId").asText()).isEqualTo("coros-42");
        assertThat(st.get("region").asText()).isEqualTo("EU");

        DeviceConnection conn = connectionRepository
                .findByAthleteIdAndProvider(athleteId, DeviceProvider.COROS).orElseThrow();
        assertThat(conn.getAccessToken()).isEqualTo("acc-1");
        assertThat(conn.getRefreshToken()).isEqualTo("ref-1");
        assertThat(conn.getApiBaseUrl()).isEqualTo("https://mcpeu.coros.com");
        assertThat(conn.getClientId()).isEqualTo("client-coros");
        assertThat(conn.getExpiresAt()).isGreaterThan(Instant.now().getEpochSecond() + 29L * 86400);
    }

    @Test
    void unStateEmisPourUnAutreAthleteEstRefuse() throws Exception {
        String foreign = stateCodec.issue(UUID.randomUUID());
        mvc.perform(post("/me/coros/connect").header("Authorization", athleteBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"c\",\"state\":\"" + foreign + "\"}"))
                .andExpect(status().isBadRequest());
        verify(corosClient, never()).exchangeCode(any(), anyString(), anyString(), anyString());
    }

    @Test
    void unEchangeRefuseParCorosNeCreePasDeConnexion() throws Exception {
        String state = stateCodec.issue(athleteId);
        when(corosClient.exchangeCode(any(), anyString(), anyString(), anyString())).thenReturn(null);
        mvc.perform(post("/me/coros/connect").header("Authorization", athleteBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"c\",\"state\":\"" + state + "\"}"))
                .andExpect(status().isBadGateway());
        assertThat(connectionRepository.findByAthleteIdAndProvider(athleteId, DeviceProvider.COROS)).isEmpty();
    }

    @Test
    void leRafraichissementGardeLeNouveauRefreshToken() throws Exception {
        connect();
        DeviceConnection conn = connectionRepository
                .findByAthleteIdAndProvider(athleteId, DeviceProvider.COROS).orElseThrow();
        conn.setExpiresAt(Instant.now().getEpochSecond() + 60); // expire dans une minute
        when(corosClient.refresh(EU, "client-coros", "ref-1"))
                .thenReturn(new TokenResponse("acc-2", "ref-2", 2_592_000L, null, null));

        assertThat(corosService.freshAccessToken(conn)).isEqualTo("acc-2");

        DeviceConnection saved = connectionRepository
                .findByAthleteIdAndProvider(athleteId, DeviceProvider.COROS).orElseThrow();
        assertThat(saved.getRefreshToken()).isEqualTo("ref-2");
    }

    @Test
    void unJetonEncoreValableNEstPasRafraichi() throws Exception {
        connect();
        DeviceConnection conn = connectionRepository
                .findByAthleteIdAndProvider(athleteId, DeviceProvider.COROS).orElseThrow();

        assertThat(corosService.freshAccessToken(conn)).isEqualTo("acc-1");
        verify(corosClient, never()).refresh(any(), anyString(), anyString());
    }

    @Test
    void seDeconnecterEffaceLesJetonsEtDemandeLaRevocation() throws Exception {
        connect();
        when(corosClient.revoke(EU, "client-coros", "ref-1")).thenReturn(true);

        mvc.perform(delete("/me/coros").header("Authorization", athleteBearer))
                .andExpect(status().isNoContent());

        verify(corosClient).revoke(EU, "client-coros", "ref-1");
        assertThat(connectionRepository.findByAthleteIdAndProvider(athleteId, DeviceProvider.COROS)).isEmpty();
    }

    @Test
    void integrationFermeeLaConnexionEstRefusee() throws Exception {
        when(corosClient.isConfigured()).thenReturn(false);

        JsonNode st = json(mvc.perform(get("/me/coros").header("Authorization", athleteBearer))
                .andExpect(status().isOk()));
        assertThat(st.get("configured").asBoolean()).isFalse();
        mvc.perform(get("/me/coros/authorize").header("Authorization", athleteBearer))
                .andExpect(status().isServiceUnavailable());
        verify(corosClient, never()).registerClient(any());
    }

    private String authorizeUrl() throws Exception {
        return json(mvc.perform(get("/me/coros/authorize").header("Authorization", athleteBearer))
                .andExpect(status().isOk())).get("url").asText();
    }

    private void connect() throws Exception {
        String state = stateCodec.issue(athleteId);
        when(corosClient.exchangeCode(eq(EU), eq("client-coros"), eq("auth-code"),
                eq(stateCodec.pkceVerifier(state))))
                .thenReturn(new TokenResponse("acc-1", "ref-1", 2_592_000L, idToken("coros-42"),
                        CorosMcpClient.SCOPES));
        mvc.perform(post("/me/coros/connect").header("Authorization", athleteBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"auth-code\",\"state\":\"" + state + "\"}"))
                .andExpect(status().isOk());
    }

    static String idToken(String sub) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        return b64.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString(("{\"sub\":\"" + sub + "\"}").getBytes(StandardCharsets.UTF_8)) + ".sig";
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
