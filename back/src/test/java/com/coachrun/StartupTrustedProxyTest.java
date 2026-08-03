package com.coachrun;

import com.coachrun.config.StartupSecretsValidator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Nombre de relais de confiance : un réglage dont l'erreur ne se voit qu'en charge.
 *
 * <p>La valeur par défaut était 1 alors que la topologie de production est client → Vercel →
 * Railway, et rien ne posait la variable. Le filtre de rate limiting retenait donc l'adresse du
 * relais Vercel — <b>la même pour tous les utilisateurs</b> — et les compteurs devenaient un seau
 * unique partagé par la plateforme entière : cinq mots de passe erronés, et plus personne ne peut
 * se connecter. On refuse ce démarrage plutôt que de le découvrir au support.</p>
 */
class StartupTrustedProxyTest {

    private static final String GOOD_JWT = "x".repeat(64);
    private static final String GOOD_KEY = "a1b2c3d4".repeat(8);
    private static final String GOOD_URL = "https://www.darilab.app";
    private static final String GOOD_CORS = "https://www.darilab.app";

    private StartupSecretsValidator withHops(int hops) {
        return new StartupSecretsValidator(GOOD_JWT, GOOD_KEY, GOOD_URL, false, "", GOOD_CORS,
                "pub", "priv", "open", "", hops);
    }

    private void run(StartupSecretsValidator validator) {
        ReflectionTestUtils.invokeMethod(validator, "validate");
    }

    @Test
    void singleHopIsRefusedBecauseItCountsTheProxyAddress() {
        assertThatThrownBy(() -> run(withHops(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RATE_LIMIT_TRUSTED_PROXY_HOPS");
    }

    @Test
    void twoHopsMatchTheVercelThenRailwayChain() {
        assertThatCode(() -> run(withHops(2))).doesNotThrowAnyException();
    }

    /** Une chaîne plus longue reste acceptable : on refuse le sous-dimensionnement, pas l'inverse. */
    @Test
    void moreHopsAreAccepted() {
        assertThatCode(() -> run(withHops(3))).doesNotThrowAnyException();
    }
}
