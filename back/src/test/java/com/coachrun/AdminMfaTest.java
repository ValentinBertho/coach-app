package com.coachrun;

import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.repository.MfaCredentialRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.security.RefreshCookie;
import com.coachrun.security.RefreshTokenService;
import com.coachrun.security.Totp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Double authentification obligatoire des administrateurs plateforme (audit du 10/11/2026).
 *
 * <p>Avant : un mot de passe suffisait à ouvrir un compte qui lit tous les clubs, emprunte
 * n'importe quel utilisateur et supprime des données.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminMfaTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private MfaCredentialRepository credentials;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RefreshTokenService refreshTokens;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User newAdmin() {
        User u = new User();
        u.setEmail("admin-" + UUID.randomUUID() + "@test.fr");
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setFullName("Admin test");
        u.setRole(UserRole.PLATFORM_ADMIN);
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerified(true);
        u.setTermsAcceptedAt(Instant.now());
        return userRepository.save(u);
    }

    private MockHttpServletResponse login(String email) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse verify(String challenge, String code) throws Exception {
        return mvc.perform(post("/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("challengeToken", challenge, "code", code))))
                .andReturn().getResponse();
    }

    private JsonNode json(MockHttpServletResponse res) throws Exception {
        return objectMapper.readTree(res.getContentAsString());
    }

    private static String codeNow(String secret) {
        return Totp.code(secret, Totp.stepAt(Instant.now().getEpochSecond()));
    }

    /** Ouvre une session complète (enrôlement compris) et rend la réponse finale. */
    private JsonNode enrol(User admin) throws Exception {
        JsonNode challenge = json(login(admin.getEmail())).get("mfa");
        return json(verify(challenge.get("challengeToken").asText(), codeNow(challenge.get("secret").asText())));
    }

    private static boolean absent(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode();
    }

    @Test
    void thePasswordAloneOpensNoAdminSession() throws Exception {
        User admin = newAdmin();
        MockHttpServletResponse res = login(admin.getEmail());

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getHeader(HttpHeaders.SET_COOKIE)).as("aucune session avant le code").isNull();
        JsonNode body = json(res);
        assertThat(absent(body.get("accessToken"))).isTrue();
        JsonNode mfa = body.get("mfa");
        assertThat(mfa.get("enrollment").asBoolean()).isTrue();
        assertThat(mfa.get("secret").asText()).matches("[A-Z2-7]{32}");
        assertThat(mfa.get("otpauthUri").asText()).startsWith("otpauth://totp/Darilab:")
                .contains("secret=" + mfa.get("secret").asText());
    }

    @Test
    void enrolmentOpensTheSessionAndHandsOutRecoveryCodesOnce() throws Exception {
        User admin = newAdmin();
        JsonNode challenge = json(login(admin.getEmail())).get("mfa");

        MockHttpServletResponse res = verify(challenge.get("challengeToken").asText(),
                codeNow(challenge.get("secret").asText()));
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getCookie(RefreshCookie.NAME)).isNotNull();
        JsonNode body = json(res);
        assertThat(body.get("recoveryCodes")).hasSize(10);
        assertThat(body.get("recoveryCodes").get(0).asText()).matches("[A-Z2-9]{4}(-[A-Z2-9]{4}){3}");

        mvc.perform(get("/admin/overview").header("Authorization", "Bearer " + body.get("accessToken").asText()))
                .andExpect(status().isOk());

        // Connexion suivante : plus de secret affiché, et pas de nouveaux codes de secours.
        JsonNode next = json(login(admin.getEmail())).get("mfa");
        assertThat(next.get("enrollment").asBoolean()).isFalse();
        assertThat(absent(next.get("secret"))).isTrue();
    }

    @Test
    void theSecretIsEncryptedAtRest() throws Exception {
        User admin = newAdmin();
        JsonNode challenge = json(login(admin.getEmail())).get("mfa");
        String secret = challenge.get("secret").asText();
        verify(challenge.get("challengeToken").asText(), codeNow(secret));

        String stored = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class).queryForObject(
                "select secret from mfa_credentials where user_id = ?", String.class, admin.getId());
        assertThat(stored).isNotEqualTo(secret).doesNotContain(secret);
    }

    @Test
    void aWrongCodeIsRefusedAndFiveKillTheChallenge() throws Exception {
        User admin = newAdmin();
        enrol(admin);
        String challenge = json(login(admin.getEmail())).get("mfa").get("challengeToken").asText();

        for (int i = 0; i < 4; i++) {
            assertThat(verify(challenge, "000000").getStatus()).isEqualTo(401);
        }
        assertThat(verify(challenge, "000000").getStatus()).as("cinquième essai : défi épuisé").isEqualTo(410);
        String secret = credentials.findById(admin.getId()).orElseThrow().getSecret();
        assertThat(verify(challenge, Totp.code(secret, Totp.stepAt(Instant.now().getEpochSecond()) + 1)).getStatus())
                .as("même un code juste ne rouvre pas un défi épuisé").isEqualTo(410);
    }

    @Test
    void aCodeServesOnlyOnce() throws Exception {
        User admin = newAdmin();
        JsonNode first = json(login(admin.getEmail())).get("mfa");
        String secret = first.get("secret").asText();
        long step = Totp.stepAt(Instant.now().getEpochSecond());
        assertThat(verify(first.get("challengeToken").asText(), Totp.code(secret, step)).getStatus()).isEqualTo(200);

        String second = json(login(admin.getEmail())).get("mfa").get("challengeToken").asText();
        assertThat(verify(second, Totp.code(secret, step)).getStatus())
                .as("code intercepté puis rejoué dans sa fenêtre").isEqualTo(401);
        assertThat(verify(second, Totp.code(secret, step + 1)).getStatus()).isEqualTo(200);
    }

    @Test
    void aRecoveryCodeWorksExactlyOnce() throws Exception {
        User admin = newAdmin();
        String recovery = enrol(admin).get("recoveryCodes").get(3).asText();

        String c1 = json(login(admin.getEmail())).get("mfa").get("challengeToken").asText();
        assertThat(verify(c1, recovery.toLowerCase().replace("-", " ")).getStatus())
                .as("saisie tolérante : minuscules, espaces").isEqualTo(200);

        String c2 = json(login(admin.getEmail())).get("mfa").get("challengeToken").asText();
        assertThat(verify(c2, recovery).getStatus()).isEqualTo(401);
    }

    @Test
    void anUnknownChallengeIsGone() throws Exception {
        assertThat(verify("inconnu", "123456").getStatus()).isEqualTo(410);
    }

    /** Session ouverte avant le déploiement, sans second facteur : elle ne se prolonge pas. */
    @Test
    void aPreMfaAdminSessionCannotBeRefreshed() throws Exception {
        User admin = newAdmin();
        String legacy = refreshTokens.issue(admin);

        MockHttpServletResponse res = mvc.perform(post("/auth/refresh").cookie(new Cookie(RefreshCookie.NAME, legacy)))
                .andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void enrolmentClosesSessionsOpenedWithoutTheSecondFactor() throws Exception {
        User admin = newAdmin();
        String legacy = refreshTokens.issue(admin);
        enrol(admin);

        assertThat(mvc.perform(post("/auth/refresh").cookie(new Cookie(RefreshCookie.NAME, legacy)))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void anEnrolledAdminSessionRefreshesNormally() throws Exception {
        User admin = newAdmin();
        JsonNode challenge = json(login(admin.getEmail())).get("mfa");
        String cookie = verify(challenge.get("challengeToken").asText(), codeNow(challenge.get("secret").asText()))
                .getCookie(RefreshCookie.NAME).getValue();

        assertThat(mvc.perform(post("/auth/refresh").cookie(new Cookie(RefreshCookie.NAME, cookie)))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void anotherAdminCanResetALostSecondFactorButNotTheirOwn() throws Exception {
        User lost = newAdmin();
        enrol(lost);
        User helper = newAdmin();
        String bearer = "Bearer " + enrol(helper).get("accessToken").asText();

        mvc.perform(post("/admin/users/{id}/reset-mfa", helper.getId()).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/users/{id}/reset-mfa", lost.getId()).header("Authorization", bearer))
                .andExpect(status().isOk());

        assertThat(credentials.existsById(lost.getId())).isFalse();
        assertThat(json(login(lost.getEmail())).get("mfa").get("enrollment").asBoolean())
                .as("réenrôlement à la connexion suivante").isTrue();
    }

    @Test
    void aCoachIsNotAskedForASecondFactor() throws Exception {
        MockHttpServletResponse res = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"coach-%s@test.fr","password":"password1234","fullName":"C",\
                                "termsAccepted": true, "clubName":"AC %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(201);
        assertThat(json(res).get("accessToken").asText()).isNotBlank();
        assertThat(absent(json(res).get("mfa"))).isTrue();
    }

    @Test
    void passwordsUnderTwelveCharactersAreRefused() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"short-%s@test.fr","password":"elevenchars","fullName":"C",\
                                "termsAccepted": true, "clubName":"AC %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    /** Vecteurs de la RFC 6238 (annexe B, SHA-1), tronqués à six chiffres. */
    @Test
    void totpMatchesTheRfcVectors() {
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"; // "12345678901234567890"
        assertThat(Totp.code(secret, Totp.stepAt(59))).isEqualTo("287082");
        assertThat(Totp.code(secret, Totp.stepAt(1111111109))).isEqualTo("081804");
        assertThat(Totp.code(secret, Totp.stepAt(1234567890))).isEqualTo("005924");
        assertThat(Totp.code(secret, Totp.stepAt(2000000000))).isEqualTo("279037");
    }
}
