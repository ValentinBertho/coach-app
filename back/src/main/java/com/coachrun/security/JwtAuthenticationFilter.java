package com.coachrun.security;

import com.coachrun.config.LogContextFilter;
import com.coachrun.entity.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Filtre stateless : valide le Bearer access token, construit un {@link AuthPrincipal}
 * (porteur du clubId) et les autorités ROLE_*. Sans token valide, la requête poursuit
 * en anonyme (les routes protégées répondront 401).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Ce filtre doit aussi s'exécuter sur le <b>dispatch asynchrone</b>.
     *
     * <h2>Le bruit que cela supprime</h2>
     *
     * <p>{@link OncePerRequestFilter} saute par défaut les dispatchs asynchrones. Or un flux SSE
     * — le badge de notifications, la messagerie temps réel — se termine précisément par un
     * dispatch asynchrone : Spring MVC repasse la requête dans la chaîne de filtres pour la
     * clore. Sans ce filtre, le contexte de sécurité y est vide ; le filtre d'autorisation de
     * Spring Security, lui, s'exécute bien, refuse la requête, et tente d'écrire un 401 sur une
     * réponse <b>déjà committée</b> (les en-têtes SSE et le premier événement sont partis depuis
     * longtemps).</p>
     *
     * <p>Le résultat était trois lignes ERROR par fermeture de flux — « Unable to handle the
     * Spring Security Exception because the response is already committed », puis
     * « Exception Processing ErrorPage » — sans qu'aucun utilisateur ne voie quoi que ce soit :
     * {@code EventSource} rouvre tout seul. Derrière un proxy qui coupe les connexions longues
     * (le cas en production), cela fait des milliers de fausses erreurs par jour et par onglet,
     * qui noient les vraies dans Sentry comme dans les journaux centralisés.</p>
     *
     * <p>Rejouer le filtre sur ce dispatch est sans effet de bord : il relit le même jeton dans
     * la même requête et repose le même principal. Le contexte est alors trouvé, l'autorisation
     * passe, et la requête se termine comme elle le devrait — en silence.</p>
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final TokenFreshnessValidator tokenFreshness;
    private final UserActivityTracker activityTracker;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            try {
                Claims claims = jwtService.parse(token);
                if (JwtService.TYPE_ACCESS.equals(claims.get("typ", String.class))
                        && !tokenBlacklist.isRevoked(claims.getId())
                        && !tokenFreshness.isStale(claims)) {
                    AuthPrincipal principal = toPrincipal(claims);
                    var authority = new SimpleGrantedAuthority("ROLE_" + principal.role().name());
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(authority));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    // Le journal sait désormais QUI a fait l'appel : l'identifiant seul, jamais
                    // l'adresse e-mail. Le nettoyage est centralisé dans LogContextFilter, qui
                    // enveloppe toute la chaîne — y compris celle-ci.
                    MDC.put(LogContextFilter.USER_ID, principal.userId().toString());
                    // Dernière activité du compte : au plus une écriture par quart d'heure
                    // (cf. UserActivityTracker). Sans elle, « utilisateurs actifs » et
                    // « à quand remonte sa dernière visite ? » restent sans réponse.
                    activityTracker.touch(principal.userId());
                }
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("JWT rejeté: {}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Token depuis l'en-tête {@code Authorization: Bearer …}, ou à défaut depuis le paramètre
     * {@code access_token} — mais uniquement sur les routes qui ne peuvent pas porter d'en-tête :
     * les flux SSE ({@code EventSource}) et les pièces jointes ouvertes dans un onglet.
     *
     * <p>Accepté partout, ce paramètre fait fuiter un jeton de session dans les journaux d'accès,
     * l'historique du navigateur et l'en-tête {@code Referer} de la moindre page.</p>
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        if (!allowsQueryToken(request)) {
            return null;
        }
        String param = request.getParameter("access_token");
        return StringUtils.hasText(param) ? param : null;
    }

    private boolean allowsQueryToken(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && (uri.endsWith("/stream") || uri.endsWith("/attachment"));
    }

    private AuthPrincipal toPrincipal(Claims claims) {
        String clubId = claims.get("clubId", String.class);
        String athleteId = claims.get("athleteId", String.class);
        return new AuthPrincipal(
                UUID.fromString(claims.getSubject()),
                clubId != null ? UUID.fromString(clubId) : null,
                athleteId != null ? UUID.fromString(athleteId) : null,
                claims.get("email", String.class),
                UserRole.valueOf(claims.get("role", String.class)));
    }
}
