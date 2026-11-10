package com.coachrun;

import com.coachrun.security.RateLimitFilter;
import com.coachrun.util.FixedWindowRateLimiter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Durcissement du rate limiting (point 7 de l'audit de bêta ouverte).
 *
 * <p>Trois défauts se combinaient : l'IP retenue était choisie par le client, aucune route
 * authentifiée n'était plafonnée, et les tables de comptage n'étaient jamais purgées — donc
 * gonflables à volonté grâce au premier défaut.</p>
 */
class RateLimitHardeningTest {

    private static final int MAX = 3;
    private static final int LOGIN_MAX = 2;
    private static final int AUTH_MAX = 4;
    private static final int EMAIL_MAX = 2;
    /** Rafraîchissement : plafond propre, volontairement large (cf. RateLimitFilter). */
    private static final int REFRESH_MAX = 60;

    /** Filtre configuré avec un seul relais de confiance (cas d'un déploiement simple). */
    private RateLimitFilter filter(int hops) {
        return new RateLimitFilter(MAX, 60, LOGIN_MAX, REFRESH_MAX, AUTH_MAX, 120, EMAIL_MAX, 3600, 50, hops);
    }

    private MockHttpServletResponse call(RateLimitFilter filter, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { /* la requête passe */ };
        ReflectionTestUtils.invokeMethod(filter, "doFilterInternal", request, response, chain);
        return response;
    }

    private MockHttpServletRequest loginFrom(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRequestURI("/api/auth/login");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    /**
     * Un client qui écrit lui-même X-Forwarded-For se plaçait en tête de chaîne et repartait
     * d'une fenêtre neuve à chaque requête : le rate limiting ne limitait plus rien.
     */
    @Test
    void spoofedForwardedForDoesNotOpenANewWindow() throws Exception {
        RateLimitFilter filter = filter(1);

        // Le proxy de confiance ajoute TOUJOURS la vraie IP en fin de chaîne ; le client bricole
        // le début pour se faire passer pour une adresse différente à chaque essai.
        for (int i = 0; i < LOGIN_MAX; i++) {
            assertThat(call(filter, loginFrom("10.0.0.9", "1.2.3." + i + ", 203.0.113.7")).getStatus())
                    .isEqualTo(200);
        }
        assertThat(call(filter, loginFrom("10.0.0.9", "9.9.9.9, 203.0.113.7")).getStatus())
                .isEqualTo(429);
    }

    /** Deux clients réellement distincts gardent bien des compteurs séparés. */
    @Test
    void distinctClientsKeepDistinctWindows() throws Exception {
        RateLimitFilter filter = filter(1);

        for (int i = 0; i < LOGIN_MAX; i++) {
            assertThat(call(filter, loginFrom("10.0.0.9", "198.51.100.1")).getStatus()).isEqualTo(200);
        }
        assertThat(call(filter, loginFrom("10.0.0.9", "198.51.100.1")).getStatus()).isEqualTo(429);
        // Une autre adresse vue par le proxy : compteur neuf.
        assertThat(call(filter, loginFrom("10.0.0.9", "198.51.100.2")).getStatus()).isEqualTo(200);
    }

    /** Chaîne plus courte qu'annoncé (en-tête forgé sans proxy) : on retombe sur l'IP TCP. */
    @Test
    void shortChainFallsBackToRemoteAddress() throws Exception {
        RateLimitFilter filter = filter(2);

        for (int i = 0; i < LOGIN_MAX; i++) {
            assertThat(call(filter, loginFrom("10.0.0.9", "1.2.3." + i)).getStatus()).isEqualTo(200);
        }
        assertThat(call(filter, loginFrom("10.0.0.9", "4.5.6.7")).getStatus()).isEqualTo(429);
    }

    /**
     * Envoi de messages, pièces jointes, import GPX : aucune de ces routes n'était plafonnée.
     * Elles retombent désormais sur un compteur par porteur de jeton.
     */
    @Test
    void authenticatedRoutesHaveAGlobalCeiling() throws Exception {
        RateLimitFilter filter = filter(1);
        String tokenA = "aaa." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"user-a\"}".getBytes()) + ".sig";

        for (int i = 0; i < AUTH_MAX; i++) {
            assertThat(call(filter, authenticated("/api/clubs/x/messages", tokenA)).getStatus())
                    .isEqualTo(200);
        }
        assertThat(call(filter, authenticated("/api/clubs/x/messages", tokenA)).getStatus())
                .isEqualTo(429);
    }

    /** Le plafond est par porteur : un utilisateur bruyant n'assèche pas le quota des autres. */
    @Test
    void ceilingIsPerBearer() throws Exception {
        RateLimitFilter filter = filter(1);
        String tokenA = "aaa.payload-a.sig";
        String tokenB = "aaa.payload-b.sig";

        for (int i = 0; i < AUTH_MAX; i++) {
            call(filter, authenticated("/api/clubs/x/messages", tokenA));
        }
        assertThat(call(filter, authenticated("/api/clubs/x/messages", tokenA)).getStatus())
                .isEqualTo(429);
        assertThat(call(filter, authenticated("/api/clubs/x/messages", tokenB)).getStatus())
                .isEqualTo(200);
    }

    /** Une requête anonyme hors route sensible n'est pas comptée (actuator, pages publiques). */
    @Test
    void anonymousNonSensitiveRoutesPassThrough() throws Exception {
        RateLimitFilter filter = filter(1);
        for (int i = 0; i < AUTH_MAX + 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actuator/health");
            request.setRequestURI("/api/actuator/health");
            request.setRemoteAddr("10.0.0.9");
            assertThat(call(filter, request).getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletRequest authenticated(String uri, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    /** Les fenêtres closes sont retirées : sans purge, la table croissait sans borne. */
    @Test
    void expiredWindowsArePurged() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(5, Duration.ofSeconds(60));
        long now = 1_000_000L;
        for (int i = 0; i < 500; i++) {
            limiter.tryAcquire("ip-" + i, now);
        }
        assertThat(limiter.trackedKeys()).isEqualTo(500);

        // Une clé reste active, les 499 autres appartiennent à une fenêtre close.
        limiter.tryAcquire("ip-0", now + 61_000L);
        assertThat(limiter.purgeExpired(now + 61_000L)).isEqualTo(499);
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }
}
