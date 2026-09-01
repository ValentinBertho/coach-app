package com.coachrun;

import com.coachrun.config.StartupConfigurationException;
import com.coachrun.config.StartupSecretsValidator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Nombre de relais de confiance : un réglage dont l'erreur ne se voit qu'en charge.
 *
 * <p>Le filtre de rate limiting compte par adresse cliente, qu'il lit dans la chaîne
 * {@code X-Forwarded-For} en remontant du nombre de relais annoncé. Annoncer <b>plus</b> de relais
 * qu'il n'y en a fait retenir l'adresse d'un proxy — la même pour tout le monde — et la plateforme
 * entière se retrouve dans un seul compteur : vingt requêtes par minute à se partager, et des
 * utilisateurs déconnectés « sans cesse ».</p>
 *
 * <p>Le navigateur atteint l'API <b>directement</b> (le front est servi par Vercel, l'API par
 * Railway — c'est d'ailleurs pourquoi il faut du CORS) : un relais est donc la topologie normale,
 * et le garde-fou ne refuse que le réglage impossible, celui qui ne compte aucun relais.</p>
 */
class StartupTrustedProxyTest {

    private static final String GOOD_JWT = "x".repeat(64);
    private static final String GOOD_KEY = "a1b2c3d4".repeat(8);
    private static final String GOOD_URL = "https://www.darilab.app";
    private static final String GOOD_CORS = "https://www.darilab.app";

    private StartupSecretsValidator withHops(int hops) {
        return new StartupSecretsValidator(GOOD_JWT, GOOD_KEY, GOOD_URL, false, "", GOOD_CORS,
                "pub", "priv", "open", "", hops, "", "", "", "", "/api");
    }

    private void run(StartupSecretsValidator validator) {
        ReflectionTestUtils.invokeMethod(validator, "validate");
    }

    /** Zéro relais ne décrit aucune topologie : il n'y a pas d'adresse cliente à lire. */
    @Test
    void zeroHopsIsRefused() {
        assertThatThrownBy(() -> run(withHops(0)))
                .isInstanceOf(StartupConfigurationException.class)
                .hasMessageContaining("RATE_LIMIT_TRUSTED_PROXY_HOPS");
    }

    /** Un relais : le navigateur parle directement à l'API. C'est la production d'aujourd'hui. */
    @Test
    void oneHopMatchesTheBrowserCallingTheApiDirectly() {
        assertThatCode(() -> run(withHops(1))).doesNotThrowAnyException();
    }

    /** Une chaîne plus longue reste acceptable : on refuse le sous-dimensionnement, pas l'inverse. */
    @Test
    void moreHopsAreAccepted() {
        assertThatCode(() -> run(withHops(2))).doesNotThrowAnyException();
        assertThatCode(() -> run(withHops(3))).doesNotThrowAnyException();
    }
}
