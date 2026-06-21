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
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";

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
        Instant now = Instant.now();
        var builder = Jwts.builder()
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
        return builder.compact();
    }
}
