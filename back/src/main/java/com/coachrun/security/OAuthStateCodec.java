package com.coachrun.security;

import com.coachrun.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Paramètre {@code state} signé pour les flux OAuth (anti-CSRF, cf. RFC 6749 §10.12).
 * Format auto-descriptif {@code <athleteId>.<expiryEpoch>.<signature>} : le front peut lire
 * l'athleteId ciblé, mais toute altération ou réutilisation tardive est rejetée à la connexion.
 */
@Component
public class OAuthStateCodec {

    private static final String HMAC_ALGO = "HmacSHA256";
    /** Domaine de signature : un state ne peut pas être confondu avec un autre HMAC du même secret. */
    private static final String DOMAIN = "oauth-state:";
    private static final long TTL_SECONDS = 600;

    private final byte[] secret;

    public OAuthStateCodec(@Value("${app.security.jwt.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(UUID athleteId) {
        long expiry = Instant.now().getEpochSecond() + TTL_SECONDS;
        String payload = athleteId + "." + expiry;
        return payload + "." + sign(payload);
    }

    /**
     * Vérifie signature, expiration et correspondance avec l'athlète ciblé.
     * @throws ApiException 400 si le state est absent, altéré, expiré ou vise un autre athlète.
     */
    public void verify(String state, UUID expectedAthleteId) {
        if (state == null || state.isBlank()) {
            throw invalid();
        }
        String[] parts = state.split("\\.");
        if (parts.length != 3) {
            throw invalid();
        }
        String payload = parts[0] + "." + parts[1];
        byte[] expected = signRaw(payload);
        byte[] actual = decode(parts[2]);
        if (actual == null || !MessageDigest.isEqual(expected, actual)) {
            throw invalid();
        }
        long expiry;
        try {
            expiry = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw invalid();
        }
        if (Instant.now().getEpochSecond() > expiry) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "La demande de connexion a expiré, relancez l'autorisation.");
        }
        if (!parts[0].equals(String.valueOf(expectedAthleteId))) {
            throw invalid();
        }
    }

    private String sign(String payload) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signRaw(payload));
    }

    private byte[] signRaw(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            return mac.doFinal((DOMAIN + payload).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC indisponible", e);
        }
    }

    private byte[] decode(String base64url) {
        try {
            return Base64.getUrlDecoder().decode(base64url);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Paramètre state invalide.");
    }
}
