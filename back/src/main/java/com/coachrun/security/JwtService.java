package com.coachrun.security;

import com.coachrun.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Génération et validation des JWT (HS512). Émet un access token court et un refresh token long,
 * distingués par le claim {@code typ}. Stateless : pas de session serveur.
 */
@Service
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_CLUB = "clubId";
    private static final String CLAIM_ATHLETE = "athleteId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";
    /**
     * Administrateur à l'origine d'une session d'impersonation. Présent uniquement sur un jeton
     * émis par {@code /admin/users/{id}/impersonate} : il rend la session identifiable pour qui
     * inspecte le jeton, et distingue un accès de débogage d'une vraie connexion de l'utilisateur.
     */
    private static final String CLAIM_IMPERSONATOR = "imp";

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.access-token-ttl-seconds:900}") long accessTtlSeconds,
            @Value("${app.security.jwt.refresh-token-ttl-seconds:2592000}") long refreshTtlSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public String generateAccessToken(User user) {
        return build(user, TYPE_ACCESS, accessTtlSeconds);
    }

    public String generateRefreshToken(User user) {
        return build(user, TYPE_REFRESH, refreshTtlSeconds);
    }

    /**
     * Jeton d'accès émis pour un administrateur qui se met à la place d'un utilisateur.
     *
     * <p><b>Pas de jeton de rafraîchissement</b> pour cette session, et c'est délibéré : une
     * impersonation dure le temps d'un jeton d'accès, pas trente jours. À l'expiration, l'écran
     * ramène l'administrateur sur son propre compte — il repart de l'endroit où il a le droit
     * d'être, plutôt que de prolonger silencieusement un accès emprunté.</p>
     */
    public String generateImpersonationToken(User target, java.util.UUID impersonatorId) {
        Instant now = Instant.now();
        return baseBuilder(target, TYPE_ACCESS, accessTtlSeconds, now)
                .claim(CLAIM_IMPERSONATOR, impersonatorId.toString())
                .compact();
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String build(User user, String type, long ttlSeconds) {
        return baseBuilder(user, type, ttlSeconds, Instant.now()).compact();
    }

    private io.jsonwebtoken.JwtBuilder baseBuilder(User user, String type, long ttlSeconds, Instant now) {
        var builder = Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key);
        if (user.getClub() != null) {
            builder.claim(CLAIM_CLUB, user.getClub().getId().toString());
        }
        if (user.getAthlete() != null) {
            builder.claim(CLAIM_ATHLETE, user.getAthlete().getId().toString());
        }
        return builder;
    }
}
