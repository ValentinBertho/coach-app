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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Rate limiting des routes sensibles (auth, réinitialisation de mot de passe, vérification
 * d'email, acceptation d'invitation). Désactivable via {@code app.rate-limit.enabled} (false en
 * tests). Réponse 429 si la limite est dépassée.
 *
 * <p>La clé est {@code IP:bucket} où le bucket est un préfixe stable : les routes porteuses de
 * token ({@code /password-reset/{token}}…) partagent le même compteur, sinon chaque token essayé
 * ouvrirait une nouvelle fenêtre et le brute-force passerait sous le radar.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    /** Bucket de la connexion : le seul où chaque requête est un essai de mot de passe. */
    public static final String LOGIN_BUCKET = "auth-login";

    private final FixedWindowRateLimiter limiter;
    private final FixedWindowRateLimiter loginLimiter;

    public RateLimitFilter(@Value("${app.rate-limit.max-requests:20}") int maxRequests,
                           @Value("${app.rate-limit.window-seconds:60}") int windowSeconds,
                           @Value("${app.rate-limit.login-max-requests:5}") int loginMaxRequests) {
        this.limiter = new FixedWindowRateLimiter(maxRequests, Duration.ofSeconds(windowSeconds));
        // La connexion a son propre seuil, bien plus strict : 20 essais par minute et par IP
        // laissent 28 800 mots de passe par jour à un attaquant, ce qui n'est pas une limite.
        this.loginLimiter = new FixedWindowRateLimiter(loginMaxRequests, Duration.ofSeconds(windowSeconds));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return bucket(request.getRequestURI()) == null;
    }

    /** Bucket de comptage de la route, ou {@code null} si elle n'est pas rate-limitée. */
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
        String key = clientIp(request) + ":" + bucket;
        FixedWindowRateLimiter applicable = LOGIN_BUCKET.equals(bucket) ? loginLimiter : limiter;
        if (!applicable.tryAcquire(key)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"message\":\"Trop de requêtes, réessayez plus tard.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
