package com.coachrun.security;

import com.coachrun.entity.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parse(token);
                if (JwtService.TYPE_ACCESS.equals(claims.get("typ", String.class))
                        && !tokenBlacklist.isRevoked(claims.getId())) {
                    AuthPrincipal principal = toPrincipal(claims);
                    var authority = new SimpleGrantedAuthority("ROLE_" + principal.role().name());
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(authority));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("JWT rejeté: {}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
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
