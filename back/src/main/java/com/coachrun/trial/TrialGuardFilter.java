package com.coachrun.trial;

import com.coachrun.exception.ApiError;
import com.coachrun.security.AuthPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Ce qu'un espace d'essai ne peut pas faire.
 *
 * <p>Tout ce qu'on vient essayer fonctionne : planifier, écrire, noter une séance, créer un
 * athlète, modifier la bibliothèque. Est refusé ce qui ferait sortir quelque chose de l'essai ou
 * le rendrait durable : inviter une vraie personne (athlète ou coach), relier Strava, Garmin,
 * COROS ou les notifications, soumettre une fiche à l'annuaire, écrire à un vrai coach de l'annuaire, et tout ce qui touche au compte lui-même
 * (mot de passe, adresse, suppression, consentement).</p>
 *
 * <p>Un compte d'essai se reconnaît à son adresse ({@link TrialService#EMAIL_SUFFIX}), portée par
 * le jeton : aucune requête en base. Le filtre est placé dans la chaîne de Spring Security, après
 * l'autorisation ({@code SecurityConfig}) : le principal est connu.</p>
 */
@Component
public class TrialGuardFilter extends OncePerRequestFilter {

    static final String MESSAGE = "Pas dans un espace d'essai. Créez votre espace pour le faire "
            + "pour de vrai : c'est gratuit.";

    /** Refusé pour toute écriture (POST, PUT, PATCH, DELETE). */
    private static final List<String> BLOCKED = List.of(
            "/auth/change-password",
            "/auth/resend-verification",
            "/me/account",
            "/me/account/**",
            "/me/consent/**",
            "/me/strava/**",
            "/me/garmin/**",
            "/me/coros/**",
            "/me/coach-profile/**",
            "/me/coach-profile",
            "/clubs/*/athletes/*/strava/**",
            "/clubs/*/athletes/*/invitation",
            "/clubs/*/members/**",
            "/me/coaching-requests",
            "/push/**");

    /** Refusé en DELETE seulement : le compte de l'athlète lui-même. */
    private static final List<String> DELETE_BLOCKED = List.of("/me");

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final ObjectMapper objectMapper;

    public TrialGuardFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isTrialUser() && blocked(request.getMethod(), path(request))) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(),
                    ApiError.of(HttpStatus.FORBIDDEN.value(), MESSAGE, request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isTrialUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal p
                && TrialService.isTrialEmail(p.email());
    }

    boolean blocked(String method, String path) {
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return false;
        }
        if (BLOCKED.stream().anyMatch(p -> matcher.match(p, path))) {
            return true;
        }
        return "DELETE".equals(method) && DELETE_BLOCKED.stream().anyMatch(p -> matcher.match(p, path));
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        return ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
    }
}
