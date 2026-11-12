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
import org.springframework.http.HttpMethod;
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
    /** Paramètre d'URL des jetons de flux. Cf. {@link StreamTokenService}. */
    private static final String STREAM_TOKEN_PARAM = "stream_token";

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
     * <p>Rejouer le filtre sur ce dispatch est sans effet de bord : il relit le même en-tête dans
     * la même requête et repose le même principal. Le contexte est alors trouvé, l'autorisation
     * passe, et la requête se termine comme elle le devrait — en silence.</p>
     *
     * <p>Un flux ouvert avec un <b>jeton de flux</b>, lui, ne se réauthentifie pas ici : le jeton
     * a été brûlé à l'ouverture, c'est tout son intérêt. Le dispatch asynchrone s'y termine
     * néanmoins en silence, parce que {@code SecurityConfig} le dispense d'autorisation — la
     * requête a déjà passé le contrôle sur son dispatch REQUEST, et la réponse est committée
     * depuis son premier événement.</p>
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final TokenFreshnessValidator tokenFreshness;
    private final UserActivityTracker activityTracker;

    /** Version du front annoncée par le client. Absente d'un appel hors navigateur : on n'écrit rien. */
    public static final String APP_VERSION_HEADER = "X-App-Version";
    private final StreamTokenService streamTokens;

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
                    authenticate(toPrincipal(claims), request);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("JWT rejeté: {}", ex.getMessage());
            }
        } else {
            authenticateWithStreamToken(request);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Jeton de session, et lui seul : l'en-tête {@code Authorization: Bearer …}.
     *
     * <p>Le paramètre d'URL {@code access_token} était accepté sur les flux et les pièces
     * jointes. Il ne l'est plus nulle part : un JWT d'accès vaut une heure sur toute l'API, et
     * une URL se retrouve dans les journaux d'accès du relais, l'historique du navigateur et le
     * {@code Referer}. Ces deux routes s'authentifient désormais avec un jeton dédié — cf.
     * {@link #authenticateWithStreamToken}.</p>
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * Authentification par jeton de flux, pour les deux requêtes qui ne peuvent pas porter
     * d'en-tête : l'ouverture d'un {@code EventSource} et l'ouverture d'une pièce jointe dans un
     * onglet.
     *
     * <p>Le jeton est à usage unique, vaut une minute, et ne vaut que pour <b>sa</b> portée : un
     * jeton de flux n'ouvre pas une pièce jointe. Il n'est lu que sur un {@code GET} — l'envoi
     * d'une pièce jointe, qui poste sur la même adresse, reste réservé à l'en-tête.</p>
     *
     * @return {@code true} si un jeton valide a été consommé et le contexte de sécurité posé
     */
    private boolean authenticateWithStreamToken(HttpServletRequest request) {
        StreamTokenService.Scope scope = scopeFor(request);
        if (scope == null) {
            return false;
        }
        String token = request.getParameter(STREAM_TOKEN_PARAM);
        if (!StringUtils.hasText(token)) {
            return false;
        }
        return streamTokens.consume(token, scope)
                .map(principal -> {
                    authenticate(principal, request);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Portée admise pour cette requête, ou {@code null} si elle n'accepte pas de jeton de flux.
     *
     * <p>Le suffixe suffit à désigner les routes concernées : {@code …/stream} pour les flux,
     * {@code …/attachment} pour les pièces jointes. Les deux existent côté coach comme côté
     * athlète, sous des préfixes différents.</p>
     */
    private StreamTokenService.Scope scopeFor(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return null;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return null;
        }
        if (uri.endsWith("/stream")) {
            return StreamTokenService.Scope.STREAM;
        }
        if (uri.endsWith("/attachment")) {
            return StreamTokenService.Scope.ATTACHMENT;
        }
        return null;
    }

    /** Pose le principal dans le contexte de sécurité et renseigne le journal. */
    private void authenticate(AuthPrincipal principal, HttpServletRequest request) {
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
        //
        // On y joint ce que le client dit de lui — son navigateur, et la version du front qu'il
        // fait tourner (X-App-Version). Le front est une PWA à service worker : un téléphone peut
        // rester des jours sur une version antérieure, et « ça ne marche pas » est alors
        // ininterprétable. Ces deux renseignements voyagent dans une écriture qui avait déjà
        // lieu, donc sans coût supplémentaire.
        activityTracker.touch(principal.userId(),
                request.getHeader("User-Agent"),
                request.getHeader(APP_VERSION_HEADER));
    }

    private AuthPrincipal toPrincipal(Claims claims) {
        String clubId = claims.get("clubId", String.class);
        String athleteId = claims.get("athleteId", String.class);
        // `imp` : l'administrateur derrière une session empruntée. Le claim était émis depuis
        // toujours et lu nulle part, si bien qu'une action faite au nom d'un utilisateur ne se
        // distinguait des siennes par rien — journal d'audit compris.
        String impersonator = claims.get("imp", String.class);
        return new AuthPrincipal(
                UUID.fromString(claims.getSubject()),
                clubId != null ? UUID.fromString(clubId) : null,
                athleteId != null ? UUID.fromString(athleteId) : null,
                claims.get("email", String.class),
                UserRole.valueOf(claims.get("role", String.class)),
                impersonator != null && !impersonator.isBlank() ? UUID.fromString(impersonator) : null);
    }
}
