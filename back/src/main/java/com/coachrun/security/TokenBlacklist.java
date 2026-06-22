package com.coachrun.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Liste noire des JWT révoqués au logout (en mémoire, par jti → expiration).
 * Nettoyage périodique des entrées expirées. (Pour le multi-instance : externaliser en Redis.)
 */
@Component
public class TokenBlacklist {

    private final ConcurrentHashMap<String, Long> revoked = new ConcurrentHashMap<>();

    public void revoke(String jti, Instant expiresAt) {
        if (jti != null && expiresAt != null) {
            revoked.put(jti, expiresAt.toEpochMilli());
        }
    }

    public boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        Long exp = revoked.get(jti);
        return exp != null && exp > System.currentTimeMillis();
    }

    @Scheduled(fixedDelay = 3_600_000L)
    void purgeExpired() {
        long now = System.currentTimeMillis();
        revoked.values().removeIf(exp -> exp <= now);
    }
}
