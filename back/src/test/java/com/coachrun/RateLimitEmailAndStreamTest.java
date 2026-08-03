package com.coachrun;

import com.coachrun.security.RateLimitFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deux trous du rate limiting, refermés.
 *
 * <p><b>Les routes à jeton en paramètre échappaient au comptage.</b> La clé d'une requête
 * authentifiée était dérivée du seul en-tête {@code Authorization}. Or les flux SSE
 * ({@code EventSource} ne sait pas poser d'en-tête) et l'ouverture d'une pièce jointe dans un
 * onglet passent le jeton en {@code access_token}. Ces routes n'avaient donc <em>aucune</em>
 * limite — alors que le proxy Vercel coupe mal les connexions longues et que le navigateur
 * rouvre automatiquement.</p>
 *
 * <p><b>Les routes qui déclenchent un e-mail retombaient sur le plafond général</b> de 300
 * requêtes/minute, soit ~300 e-mails/minute pour un compte légitime. Le renvoi de vérification
 * régénère un lien à chaque appel, et le changement d'adresse envoie vers une adresse
 * <em>arbitraire</em>. Le plan d'envoi de la bêta est à 100 e-mails/jour, et il est partagé avec
 * les réinitialisations de mot de passe.</p>
 */
class RateLimitEmailAndStreamTest {

    private static final int MAX = 20;
    private static final int LOGIN_MAX = 5;
    private static final int AUTH_MAX = 4;
    private static final int EMAIL_MAX = 2;

    /** Jeton JWT factice : le filtre n'en lit que la charge utile, sans la valider. */
    private static final String TOKEN = "aaa.bbbbbbbb.ccc";

    private RateLimitFilter filter() {
        return new RateLimitFilter(MAX, 60, LOGIN_MAX, AUTH_MAX, EMAIL_MAX, 3600, 2);
    }

    private int call(RateLimitFilter filter, MockHttpServletRequest request) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { /* la requête passe */ };
        ReflectionTestUtils.invokeMethod(filter, "doFilterInternal", request, response, chain);
        return response.getStatus();
    }

    private MockHttpServletRequest streamRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications/stream");
        request.setParameter("access_token", TOKEN);
        request.setRemoteAddr("203.0.113.9");
        return request;
    }

    @Test
    void sseStreamCarryingTokenInQueryIsCounted() {
        RateLimitFilter filter = filter();
        for (int i = 0; i < AUTH_MAX; i++) {
            assertThat(call(filter, streamRequest()))
                    .as("reconnexion n°%d dans le plafond", i + 1)
                    .isEqualTo(200);
        }
        assertThat(call(filter, streamRequest()))
                .as("la reconnexion en boucle finit par être freinée")
                .isEqualTo(429);
    }

    @Test
    void resendVerificationIsCappedFarBelowTheGeneralLimit() {
        RateLimitFilter filter = filter();
        for (int i = 0; i < EMAIL_MAX; i++) {
            assertThat(call(filter, verificationRequest())).isEqualTo(200);
        }
        assertThat(call(filter, verificationRequest()))
                .as("un compte ne peut pas vider le quota d'envoi à lui seul")
                .isEqualTo(429);
    }

    /** Le changement d'adresse envoie une vérification vers une adresse arbitraire : même seuil. */
    @Test
    void patchProfileIsCappedLikeAnEmailRoute() {
        RateLimitFilter filter = filter();
        for (int i = 0; i < EMAIL_MAX; i++) {
            assertThat(call(filter, patchMeRequest("PATCH"))).isEqualTo(200);
        }
        assertThat(call(filter, patchMeRequest("PATCH"))).isEqualTo(429);
    }

    /** Lire son profil n'envoie aucun e-mail : le seuil strict ne doit pas s'y appliquer. */
    @Test
    void readingOwnProfileIsNotTreatedAsAnEmailRoute() {
        RateLimitFilter filter = filter();
        for (int i = 0; i <= EMAIL_MAX; i++) {
            assertThat(call(filter, patchMeRequest("GET"))).isEqualTo(200);
        }
    }

    /**
     * Le retour de séance de l'athlète finit lui aussi par « /feedback » : il ne doit pas tomber
     * dans le seau par IP du dépôt de retours, sinon tous les athlètes d'un club derrière une même
     * box se le partagent.
     */
    @Test
    void athleteWorkoutFeedbackIsNotTheBetaFeedbackBucket() {
        assertThat(RateLimitFilter.bucket("/api/me/workouts/abc/feedback")).isNull();
        assertThat(RateLimitFilter.bucket("/api/feedback")).isEqualTo("beta-feedback");
    }

    private MockHttpServletRequest verificationRequest() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/auth/resend-verification");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        request.setRemoteAddr("203.0.113.9");
        return request;
    }

    private MockHttpServletRequest patchMeRequest(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/auth/me");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        request.setRemoteAddr("203.0.113.9");
        return request;
    }
}
