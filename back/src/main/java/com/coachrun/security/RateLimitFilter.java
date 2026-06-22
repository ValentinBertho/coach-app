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
 * Rate limiting des routes sensibles (auth, acceptation d'invitation). Désactivable via
 * {@code app.rate-limit.enabled} (false en tests). Réponse 429 si la limite est dépassée.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private final FixedWindowRateLimiter limiter;

    public RateLimitFilter(@Value("${app.rate-limit.max-requests:20}") int maxRequests,
                           @Value("${app.rate-limit.window-seconds:60}") int windowSeconds) {
        this.limiter = new FixedWindowRateLimiter(maxRequests, Duration.ofSeconds(windowSeconds));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean sensitive = uri.endsWith("/auth/login")
                || uri.endsWith("/auth/register")
                || uri.contains("/invitations/") && uri.endsWith("/accept");
        return !sensitive;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String key = clientIp(request) + ":" + request.getRequestURI();
        if (!limiter.tryAcquire(key)) {
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
