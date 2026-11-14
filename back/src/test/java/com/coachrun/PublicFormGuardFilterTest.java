package com.coachrun;

import com.coachrun.security.PublicFormGuardFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le piège à robots des formulaires publics et les plafonds quotidiens globaux.
 *
 * <p>Ce qui compte autant que le refus du robot : un humain dont le navigateur charge encore
 * l'ancien front (aucun en-tête) passe, et un robot refusé n'entame pas le quota des vraies
 * personnes.</p>
 */
class PublicFormGuardFilterTest {

    private static final int SIGNUP_MAX = 2;
    private static final int RESET_MAX = 1;

    private PublicFormGuardFilter filter() {
        return new PublicFormGuardFilter(2000, SIGNUP_MAX, RESET_MAX);
    }

    private int call(PublicFormGuardFilter filter, MockHttpServletRequest request) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { /* la requête passe */ };
        ReflectionTestUtils.invokeMethod(filter, "doFilterInternal", request, response, chain);
        return response.getStatus();
    }

    private MockHttpServletRequest post(String uri, String trap, String elapsed) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        if (trap != null) {
            request.addHeader(PublicFormGuardFilter.TRAP_HEADER, trap);
        }
        if (elapsed != null) {
            request.addHeader(PublicFormGuardFilter.ELAPSED_HEADER, elapsed);
        }
        return request;
    }

    @Test
    void humanFillingTheFormPasses() {
        assertThat(call(filter(), post("/api/public/athlete-registration", "", "8000"))).isEqualTo(200);
    }

    @Test
    void filledTrapIsRejected() {
        assertThat(call(filter(), post("/api/public/athlete-registration", "https://spam.example", "8000")))
                .isEqualTo(400);
    }

    @Test
    void tooFastSubmissionIsRejected() {
        assertThat(call(filter(), post("/api/public/club-requests", "", "300"))).isEqualTo(400);
        assertThat(call(filter(), post("/api/public/password-reset", "", "300"))).isEqualTo(400);
        assertThat(call(filter(), post("/api/public/coaches/jean-dupont/report", "", "300")))
                .isEqualTo(400);
    }

    @Test
    void forgedElapsedIsRejected() {
        assertThat(call(filter(), post("/api/auth/register", "", "abc"))).isEqualTo(400);
    }

    @Test
    void missingHeadersPass() {
        // Un onglet ouvert avant le déploiement charge l'ancien front : il ne doit pas être bloqué.
        assertThat(call(filter(), post("/api/public/athlete-registration", null, null))).isEqualTo(200);
    }

    @Test
    void otherRoutesAreIgnored() {
        assertThat(call(filter(), post("/api/auth/login", "rempli", "1"))).isEqualTo(200);
        assertThat(call(filter(), post("/api/public/password-reset/abc123", "rempli", "1")))
                .as("l'application du nouveau mot de passe n'envoie rien et n'est pas un formulaire public")
                .isEqualTo(200);
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/public/coaches");
        get.addHeader(PublicFormGuardFilter.TRAP_HEADER, "rempli");
        assertThat(call(filter(), get)).isEqualTo(200);
    }

    @Test
    void signupsHaveAGlobalDailyCapAcrossAllAddresses() {
        PublicFormGuardFilter filter = filter();
        for (int i = 0; i < SIGNUP_MAX; i++) {
            MockHttpServletRequest request = post("/api/public/athlete-registration", "", "5000");
            request.setRemoteAddr("198.51.100." + i);
            assertThat(call(filter, request)).isEqualTo(200);
        }
        MockHttpServletRequest fromNewAddress = post("/api/public/club-requests", "", "5000");
        fromNewAddress.setRemoteAddr("203.0.113.77");
        assertThat(call(filter, fromNewAddress))
                .as("changer d'adresse IP ne rouvre pas le plafond global")
                .isEqualTo(429);
    }

    @Test
    void passwordResetKeepsItsOwnReserve() {
        PublicFormGuardFilter filter = filter();
        for (int i = 0; i < SIGNUP_MAX + 3; i++) {
            call(filter, post("/api/public/athlete-registration", "", "5000"));
        }
        assertThat(call(filter, post("/api/public/password-reset", "", "5000")))
                .as("une vague d'inscriptions ne doit pas empêcher de récupérer son compte")
                .isEqualTo(200);
        assertThat(call(filter, post("/api/public/password-reset", "", "5000"))).isEqualTo(429);
    }

    @Test
    void trappedRobotsDoNotConsumeTheCap() {
        PublicFormGuardFilter filter = filter();
        for (int i = 0; i < 10; i++) {
            assertThat(call(filter, post("/api/public/athlete-registration", "spam", "5000")))
                    .isEqualTo(400);
        }
        assertThat(call(filter, post("/api/public/athlete-registration", "", "5000"))).isEqualTo(200);
    }
}
