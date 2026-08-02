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
    /** Lecture de la réponse : Resend et Strava répondent en centaines de millisecondes. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private HttpClients() {
    }

    /** Fabrique partagée : connexion 3 s, lecture 10 s. */
    public static ClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
