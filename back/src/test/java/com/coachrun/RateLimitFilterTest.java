package com.coachrun;

import com.coachrun.security.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Couverture des routes rate-limitées : les routes porteuses de token partagent un bucket
 * stable (sinon chaque token essayé ouvrirait sa propre fenêtre de comptage).
 */
class RateLimitFilterTest {

    @Test
    void sensitiveRoutesAreBucketed() {
        assertThat(RateLimitFilter.bucket("/api/auth/login")).isEqualTo("auth-login");
        assertThat(RateLimitFilter.bucket("/api/auth/register")).isEqualTo("auth-register");
        assertThat(RateLimitFilter.bucket("/api/auth/refresh")).isEqualTo("auth-refresh");
        assertThat(RateLimitFilter.bucket("/api/public/password-reset")).isEqualTo("password-reset");
        assertThat(RateLimitFilter.bucket("/api/public/verify-email/tok-b")).isEqualTo("verify-email");
        assertThat(RateLimitFilter.bucket("/api/public/invitations/tok/accept")).isEqualTo("invitation-accept");
    }

    @Test
    void tokenRoutesShareOneBucketRegardlessOfToken() {
        assertThat(RateLimitFilter.bucket("/api/public/password-reset/tok-a"))
                .isEqualTo(RateLimitFilter.bucket("/api/public/password-reset/tok-b"));
        assertThat(RateLimitFilter.bucket("/api/public/verify-email/t1"))
                .isEqualTo(RateLimitFilter.bucket("/api/public/verify-email/t2"));
    }

    @Test
    void ordinaryRoutesAreNotLimited() {
        assertThat(RateLimitFilter.bucket("/api/auth/me")).isNull();
        assertThat(RateLimitFilter.bucket("/api/clubs/x/athletes")).isNull();
        assertThat(RateLimitFilter.bucket("/api/public/ping")).isNull();
    }

    /** Filtre de test : plafonds serrés pour que le dépassement tienne en quelques requêtes. */
    private RateLimitFilter filter(int general, int refresh, int trustedHops) {
        return new RateLimitFilter(general, 60, 5, refresh, 90, 300, 120, 3, 3600, 5, trustedHops);
    }

    private int call(RateLimitFilter filter, String uri, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        request.setRemoteAddr("10.0.0.1"); // adresse du relais : la même pour tout le monde
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    /**
     * Le rafraîchissement ne partage plus le plafond anti-devinage. Il est légitime et fréquent —
     * chaque retour au premier plan en déclenche un — et le franchir déconnectait l'utilisateur,
     * le client lisant le 429 comme un refus de session.
     */
    @Test
    void refreshHasItsOwnGenerousCeiling() throws Exception {
        RateLimitFilter filter = filter(2, 10, 1);

        for (int i = 0; i < 10; i++) {
            assertThat(call(filter, "/api/auth/refresh", "203.0.113.7")).isEqualTo(200);
        }
        assertThat(call(filter, "/api/auth/refresh", "203.0.113.7")).isEqualTo(429);

        // Le seuil général, lui, reste serré : deux passages, puis refus.
        assertThat(call(filter, "/api/public/password-reset", "203.0.113.7")).isEqualTo(200);
        assertThat(call(filter, "/api/public/password-reset", "203.0.113.7")).isEqualTo(200);
        assertThat(call(filter, "/api/public/password-reset", "203.0.113.7")).isEqualTo(429);
    }

    /**
     * Déclarer un relais de trop réunit tous les clients dans un compteur unique.
     *
     * <p>C'est le défaut qui déconnectait « sans cesse ». La chaîne annoncée est plus longue que
     * la réelle, sa lecture échoue, et le filtre retombe sur l'adresse de la connexion TCP —
     * c'est-à-dire celle du relais, identique pour tout le monde. Le plafond destiné à contenir
     * un attaquant éjecte alors la cohorte entière.</p>
     *
     * <p>Le repli est délibérément conservé : l'adresse TCP est la seule que le client ne peut
     * pas choisir, et s'en écarter rouvrirait le contournement que
     * {@code RateLimitHardeningTest} verrouille. Ce test fige donc la conséquence, pour que la
     * valeur de {@code trusted-proxy-hops} reste comprise comme un fait de topologie et non comme
     * un réglage de confort.</p>
     */
    @Test
    void declaringOneProxyTooManyPutsEveryClientInTheSameBucket() throws Exception {
        RateLimitFilter filter = filter(1, 10, 2); // deux relais annoncés, un seul présent

        assertThat(call(filter, "/api/public/password-reset", "203.0.113.7")).isEqualTo(200);
        // Un client tout à fait distinct, et pourtant déjà hors quota.
        assertThat(call(filter, "/api/public/password-reset", "198.51.100.4")).isEqualTo(429);
    }

    /** Topologie correctement déclarée : c'est bien le client, et non le relais, qui est compté. */
    @Test
    void aWellDeclaredChainReadsTheClientHop() throws Exception {
        RateLimitFilter filter = filter(1, 10, 2);

        assertThat(call(filter, "/api/public/password-reset", "203.0.113.7, 70.41.3.18")).isEqualTo(200);
        assertThat(call(filter, "/api/public/password-reset", "203.0.113.7, 70.41.3.18")).isEqualTo(429);
        assertThat(call(filter, "/api/public/password-reset", "198.51.100.4, 70.41.3.18")).isEqualTo(200);
    }
}
