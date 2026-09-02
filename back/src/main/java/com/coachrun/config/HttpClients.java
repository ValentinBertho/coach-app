package com.coachrun.config;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Fabriques HTTP sortantes avec des délais explicites.
 *
 * <p>Un {@code RestClient} construit sans {@code requestFactory} n'a <b>aucun</b> délai de
 * lecture : si le service distant accepte la connexion puis ne répond jamais, le thread appelant
 * attend indéfiniment. Sur l'envoi d'e-mail, ce thread détient en plus une connexion du pool
 * Hikari (10 par défaut) — dix envois bloqués suffisent à figer toute l'API.</p>
 */
public final class HttpClients {

    /** Établissement de la connexion : au-delà, l'hôte est injoignable, inutile d'attendre. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    /** Lecture par défaut : un appel qui rend un petit corps répond en centaines de millisecondes. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Lecture d'une page d'activités Strava.
     *
     * <p>Le défaut de 10 s reposait sur l'idée que « Strava répond en centaines de
     * millisecondes ». C'est vrai d'un rafraîchissement de jeton ; ça ne l'est pas de
     * {@code /athlete/activities}, qui rend jusqu'à cent activités complètes en une réponse. En
     * production, la passe horaire expirait sur ce délai — athlète après athlète, à dix secondes
     * pile — et le filet de rattrapage ne rattrapait plus rien.</p>
     *
     * <p>Trente secondes, et pas davantage : le contrat de {@code OutboundResilienceTest} est
     * qu'aucun appel sortant ne puisse retenir un thread au-delà. Le plafond de pages
     * ({@code MAX_PAGES}) borne le reste.</p>
     *
     * <p>Réduire {@code per_page} aurait été l'autre voie. Elle a été écartée : elle multiplie
     * les requêtes, et le quota d'appels Strava est partagé par tous les athlètes du club.</p>
     */
    private static final Duration STRAVA_READ_TIMEOUT = Duration.ofSeconds(30);

    private HttpClients() {
    }

    /** Fabrique partagée : connexion 3 s, lecture 10 s. */
    public static ClientHttpRequestFactory timeouts() {
        return factory(READ_TIMEOUT);
    }

    /** Fabrique de l'API Strava : connexion 3 s, lecture 30 s (cf. {@link #STRAVA_READ_TIMEOUT}). */
    public static ClientHttpRequestFactory stravaApiTimeouts() {
        return factory(STRAVA_READ_TIMEOUT);
    }

    private static ClientHttpRequestFactory factory(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
