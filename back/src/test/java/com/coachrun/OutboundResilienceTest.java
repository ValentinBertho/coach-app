package com.coachrun;

import com.coachrun.config.HttpClients;
import com.coachrun.integration.ResendMailClient;
import com.coachrun.integration.StravaClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Résilience des appels sortants et du planificateur.
 *
 * <p>Sans délai de lecture, un service distant qui accepte la connexion puis ne répond jamais
 * bloque le thread appelant sans fin — et sur l'envoi d'e-mail, ce thread détient en plus une
 * connexion du pool Hikari. Sans pool de planification, les cinq jobs se partagent un seul
 * thread : un import Strava lent décale le rappel de 18 h et le digest du matin.</p>
 *
 * <p>Test unitaire volontairement sans contexte Spring : les tests d'intégration du projet
 * partagent une unique base H2 en mémoire, et un second contexte y rejouerait Liquibase.</p>
 */
class OutboundResilienceTest {

    @Test
    void resendClientHasTimeouts() {
        ResendMailClient client = new ResendMailClient("re_test", "Darilab <no-reply@darilab.app>");
        assertTimeouts(requestFactoryOf(ReflectionTestUtils.getField(client, "restClient")));
    }

    @Test
    void stravaClientsHaveTimeouts() {
        StravaClient client = new StravaClient("id", "secret", "https://www.darilab.app/app/strava/callback");
        assertTimeouts(requestFactoryOf(ReflectionTestUtils.getField(client, "oauth")));
        assertTimeouts(requestFactoryOf(ReflectionTestUtils.getField(client, "api")));
    }

    /** Le planificateur dispose de plusieurs threads : un job lent n'en bloque plus quatre autres. */
    @Test
    void schedulerPoolIsSized() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties props = yaml.getObject();

        assertThat(props).isNotNull();
        assertThat(Integer.parseInt(props.getProperty("spring.task.scheduling.pool.size")))
                .isGreaterThanOrEqualTo(4);
    }

    /** La fabrique partagée porte bien les deux délais annoncés. */
    @Test
    void sharedFactoryCarriesBothTimeouts() {
        assertTimeouts(HttpClients.timeouts());
    }

    /**
     * Fabrique HTTP réellement portée par un {@code RestClient}. On balaie ses champs plutôt que
     * d'en coder le nom en dur : c'est un détail interne de Spring, susceptible de changer de nom
     * d'une version à l'autre.
     */
    private Object requestFactoryOf(Object restClient) {
        assertThat(restClient).isInstanceOf(RestClient.class);
        for (Field f : restClient.getClass().getDeclaredFields()) {
            if (ClientHttpRequestFactory.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                try {
                    return f.get(restClient);
                } catch (IllegalAccessException e) {
                    throw new AssertionError("Fabrique HTTP illisible sur " + restClient.getClass(), e);
                }
            }
        }
        throw new AssertionError("Aucune fabrique HTTP sur " + restClient.getClass());
    }

    private void assertTimeouts(Object factory) {
        assertThat(factory).isInstanceOf(SimpleClientHttpRequestFactory.class);
        assertThat(readMillis(factory, "connectTimeout")).isPositive().isLessThanOrEqualTo(5_000);
        assertThat(readMillis(factory, "readTimeout")).isPositive().isLessThanOrEqualTo(30_000);
    }

    private int readMillis(Object factory, String field) {
        try {
            Field f = SimpleClientHttpRequestFactory.class.getDeclaredField(field);
            f.setAccessible(true);
            return (int) f.get(factory);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Champ " + field + " introuvable sur la fabrique HTTP", e);
        }
    }
}
