package com.coachrun.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Contexte de journalisation d'une requête : identifiant, méthode, chemin — et, dès que
 * l'authentification a eu lieu, l'utilisateur (cf. {@code JwtAuthenticationFilter}).
 *
 * <p><b>Pourquoi.</b> Les lignes de journal de cette application sont utiles mais anonymes :
 * « File d'événements Strava saturée », « Connexion verrouillée », « Refresh refusé ». Sans
 * contexte, impossible de rattacher deux lignes à la même requête, ni de retrouver toutes les
 * lignes d'un incident à partir du {@code correlationId} qu'on a renvoyé à l'utilisateur.
 * Posés en MDC, ces champs deviennent des colonnes cherchables côté Better Stack
 * (cf. {@code mdcFields} dans {@code logback-spring.xml}) plutôt que du texte noyé dans le
 * message.</p>
 *
 * <p><b>Ce qui n'y entre pas, délibérément.</b> Ni la chaîne de requête, ni les en-têtes. Cette
 * API accepte un jeton d'accès en paramètre d'URL sur les flux SSE et les pièces jointes
 * ({@code JwtAuthenticationFilter#allowsQueryToken}) : journaliser {@code getQueryString()}
 * enverrait des jetons de session valides chez un tiers, et les y laisserait pour la durée de
 * rétention. {@code getRequestURI()} s'arrête avant le {@code ?}. Aucune donnée personnelle non
 * plus — l'identifiant d'utilisateur est un UUID, jamais l'adresse e-mail.</p>
 *
 * <p>Placé en tête de chaîne pour envelopper <b>tout</b> le traitement, filtres de sécurité
 * compris : c'est son {@code finally} qui nettoie le MDC. Sans ce nettoyage, les threads du
 * pool Tomcat étant réutilisés, la requête suivante hériterait du contexte de la précédente —
 * un journal qui attribue une erreur au mauvais utilisateur est pire que pas de journal.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LogContextFilter extends OncePerRequestFilter {

    /** Identifiant de la requête : relie entre elles les lignes d'un même appel. */
    public static final String REQUEST_ID = "requestId";
    /** Identifiant de l'utilisateur authentifié, posé par le filtre JWT une fois le jeton validé. */
    public static final String USER_ID = "userId";
    /** Identifiant de corrélation d'une 500, celui-là même qui est renvoyé à l'utilisateur. */
    public static final String CORRELATION_ID = "correlationId";

    private static final String METHOD = "method";
    private static final String PATH = "path";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        MDC.put(REQUEST_ID, newRequestId());
        MDC.put(METHOD, request.getMethod());
        // Sans la chaîne de requête : elle peut porter un access_token (SSE, pièces jointes).
        MDC.put(PATH, request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    /** Douze caractères : assez pour ne pas se répéter dans un journal, assez court pour se dicter. */
    private static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
