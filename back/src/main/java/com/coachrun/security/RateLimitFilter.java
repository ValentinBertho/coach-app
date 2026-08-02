package com.coachrun.security;

import com.coachrun.util.FixedWindowRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Rate limiting des routes sensibles (auth, réinitialisation de mot de passe, vérification
 * d'email, acceptation d'invitation) <b>et</b> plafond global par utilisateur authentifié.
 * Désactivable via {@code app.rate-limit.enabled} (false en tests). Réponse 429 au dépassement.
 *
 * <p>La clé est {@code IP:bucket} où le bucket est un préfixe stable : les routes porteuses de
 * token ({@code /password-reset/{token}}…) partagent le même compteur, sinon chaque token essayé
 * ouvrirait une nouvelle fenêtre et le brute-force passerait sous le radar.</p>
 *
 * <p>Les routes authentifiées (envoi de messages, pièces jointes, import GPX) n'avaient jusqu'ici
 * aucune limite : un compte légitime — ou volé — pouvait saturer base et stockage sans jamais
 * être ralenti. Elles retombent désormais sur un plafond large, compté <b>par porteur de
 * jeton</b> : il n'entrave pas l'usage normal, il coupe l'emballement.</p>
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    /** Bucket de la connexion : le seul où chaque requête est un essai de mot de passe. */
    public static final String LOGIN_BUCKET = "auth-login";
    /** Bucket fourre-tout des routes authentifiées, compté par porteur de jeton. */
    public static final String AUTHENTICATED_BUCKET = "api-authenticated";

    private final FixedWindowRateLimiter limiter;
    private final FixedWindowRateLimiter loginLimiter;
    private final FixedWindowRateLimiter authenticatedLimiter;
    private final int trustedProxyHops;

    public RateLimitFilter(@Value("${app.rate-limit.max-requests:20}") int maxRequests,
                           @Value("${app.rate-limit.window-seconds:60}") int windowSeconds,
                           @Value("${app.rate-limit.login-max-requests:5}") int loginMaxRequests,
                           @Value("${app.rate-limit.authenticated-max-requests:300}") int authenticatedMax,
                           @Value("${app.rate-limit.trusted-proxy-hops:1}") int trustedProxyHops) {
        this.limiter = new FixedWindowRateLimiter(maxRequests, Duration.ofSeconds(windowSeconds));
        // La connexion a son propre seuil, bien plus strict : 20 essais par minute et par IP
        // laissent 28 800 mots de passe par jour à un attaquant, ce qui n'est pas une limite.
        this.loginLimiter = new FixedWindowRateLimiter(loginMaxRequests, Duration.ofSeconds(windowSeconds));
        // Plafond global : large (une navigation soutenue reste très en dessous), mais il existe.
        this.authenticatedLimiter =
                new FixedWindowRateLimiter(authenticatedMax, Duration.ofSeconds(windowSeconds));
        this.trustedProxyHops = Math.max(1, trustedProxyHops);
    }

    /** Bucket de comptage de la route, ou {@code null} si elle n'est pas rate-limitée par IP. */
    public static String bucket(String uri) {
        if (uri.endsWith("/auth/login")) {
            return LOGIN_BUCKET;
        }
        if (uri.endsWith("/auth/register")) {
            return "auth-register";
        }
        if (uri.endsWith("/auth/refresh")) {
            return "auth-refresh";
        }
        if (uri.contains("/public/password-reset")) {
            return "password-reset";
        }
        if (uri.contains("/public/verify-email")) {
            return "verify-email";
        }
        if (uri.contains("/invitations/") && uri.endsWith("/accept")) {
            return "invitation-accept";
        }
        return null;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String bucket = bucket(request.getRequestURI());
        String key;
        FixedWindowRateLimiter applicable;

        if (bucket != null) {
            key = clientIp(request) + ":" + bucket;
            applicable = LOGIN_BUCKET.equals(bucket) ? loginLimiter : limiter;
        } else {
            // Route non listée : plafond global par porteur de jeton. Les requêtes anonymes hors
            // buckets (actuator, pages publiques) ne sont pas comptées ici.
            String bearerKey = bearerKey(request);
            if (bearerKey == null) {
                filterChain.doFilter(request, response);
                return;
            }
            key = bearerKey + ":" + AUTHENTICATED_BUCKET;
            applicable = authenticatedLimiter;
        }

        if (!applicable.tryAcquire(key)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"status\":429,\"message\":\"Trop de requêtes, réessayez plus tard.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Clé de comptage d'une requête authentifiée, dérivée du jeton porté.
     *
     * <p>Compter par porteur plutôt que par IP évite de pénaliser un club entier derrière une même
     * sortie réseau, et suit l'emballement là où il se produit. Le filtre s'exécute avant
     * l'authentification : on ne décode ni ne valide le jeton (c'est le rôle du filtre
     * d'authentification), on se contente d'une empreinte stable de sa charge utile. Un jeton
     * forgé n'ouvre aucun accès — au pire il choisit son propre compteur, ce qui ne dessert
     * que lui.</p>
     */
    private String bearerKey(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7);
        int first = token.indexOf('.');
        int second = token.indexOf('.', first + 1);
        if (first < 0 || second < 0) {
            return null;
        }
        return "u:" + Integer.toHexString(token.substring(first + 1, second).hashCode());
    }

    /**
     * IP réellement attribuée par le proxy de confiance.
     *
     * <p>Prendre le <b>premier</b> élément de {@code X-Forwarded-For} était exploitable : cet
     * en-tête est fourni par le client, qui pouvait donc écrire ce qu'il voulait en tête de liste
     * et s'ouvrir une fenêtre neuve à chaque requête — le rate limiting ne limitait plus rien.
     * Chaque relais <b>ajoute</b> son observation en fin de chaîne : derrière
     * {@code trustedProxyHops} relais de confiance (Vercel puis Railway), l'adresse à retenir est
     * la n-ième en partant de la fin.</p>
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] hops = forwarded.split(",");
        int index = hops.length - trustedProxyHops;
        if (index < 0 || index >= hops.length) {
            // Moins de relais qu'annoncé : chaîne incomplète ou falsifiée. On retombe sur
            // l'adresse de la connexion TCP, la seule que le client ne peut pas choisir.
            return request.getRemoteAddr();
        }
        String ip = hops[index].trim();
        return ip.isEmpty() ? request.getRemoteAddr() : ip;
    }

    /**
     * Purge des fenêtres closes. Les tables sont indexées sur des clés que l'appelant contrôle
     * (IP, porteur de jeton) : sans purge, elles croissaient sans borne — et le défaut du
     * {@code X-Forwarded-For} offrait précisément de quoi les faire enfler à volonté.
     */
    @Scheduled(fixedDelay = 600_000L)
    void purgeExpiredWindows() {
        limiter.purgeExpired();
        loginLimiter.purgeExpired();
        authenticatedLimiter.purgeExpired();
    }
}
