package com.coachrun;

import com.coachrun.config.LogContextFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contexte de journalisation d'une requête.
 *
 * <p>Deux propriétés seulement, mais ce sont celles dont dépend l'exploitabilité du journal
 * centralisé : les champs sont bien posés (sinon Better Stack n'a que du texte à indexer), et
 * ils sont bien <b>effacés</b> à la sortie. Le second point est le plus important : les threads
 * du pool Tomcat sont réutilisés, et un MDC qui survit à sa requête attribue les lignes suivantes
 * au mauvais utilisateur — un journal faux coûte plus cher qu'un journal absent.</p>
 */
class LogContextFilterTest {

    private final LogContextFilter filter = new LogContextFilter();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void posesRequestContextForTheDurationOfTheCall() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        var seen = new java.util.HashMap<String, String>();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                Map<String, String> context = MDC.getCopyOfContextMap();
                if (context != null) {
                    seen.putAll(context);
                }
            }
        });

        assertThat(seen.get("method")).isEqualTo("POST");
        assertThat(seen.get("path")).isEqualTo("/api/auth/login");
        assertThat(seen.get(LogContextFilter.REQUEST_ID)).isNotBlank();
    }

    /**
     * Le chemin est journalisé, jamais la chaîne de requête : les flux SSE et les pièces jointes
     * portent leur jeton d'accès en paramètre d'URL. La journaliser enverrait des jetons de
     * session valides chez un tiers, et les y laisserait pour toute la durée de rétention.
     */
    @Test
    void neverLogsTheQueryString() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/me/notifications/stream");
        request.setQueryString("access_token=un-jeton-parfaitement-valide");
        var seen = new java.util.HashMap<String, String>();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                Map<String, String> context = MDC.getCopyOfContextMap();
                if (context != null) {
                    seen.putAll(context);
                }
            }
        });

        assertThat(seen.values()).noneMatch(v -> v.contains("access_token"));
        assertThat(seen.get("path")).isEqualTo("/api/me/notifications/stream");
    }

    /** Le thread rendu au pool ne doit rien conserver — y compris quand la requête a échoué. */
    @Test
    void clearsContextEvenWhenTheRequestBlowsUp() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clubs/x/athletes");

        try {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
                @Override
                public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                    // Un filtre en aval pose l'utilisateur, puis le traitement échoue.
                    MDC.put(LogContextFilter.USER_ID, "11111111-1111-1111-1111-111111111111");
                    throw new IllegalStateException("boom");
                }
            });
        } catch (Exception expected) {
            // C'est le nettoyage qui est vérifié, pas la propagation.
        }

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
